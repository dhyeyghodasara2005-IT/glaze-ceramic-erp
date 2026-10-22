package com.ceramicerp.store;

import com.ceramicerp.common.BaseEntity;
import com.ceramicerp.common.PageResponse;
import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.master.Machine;
import com.ceramicerp.master.MachineRepository;
import com.ceramicerp.master.Material;
import com.ceramicerp.master.MaterialRepository;
import com.ceramicerp.numbering.DocumentNumberService;
import com.ceramicerp.organization.Plant;
import com.ceramicerp.organization.PlantService;
import com.ceramicerp.party.Party;
import com.ceramicerp.party.PartyRepository;
import com.ceramicerp.party.PartyType;
import com.ceramicerp.security.CurrentUser;
import com.ceramicerp.security.PlantAccess;
import com.ceramicerp.store.dto.StoreDocumentRequest;
import com.ceramicerp.store.dto.StoreDocumentResponse;
import com.ceramicerp.store.dto.StoreDocumentSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * GRN, material issue and stock adjustment documents.
 *
 * Life of a document:
 *   DRAFT     - saved, can be edited or deleted; stock is NOT touched yet
 *   POSTED    - gets its number, stock moves; can no longer be edited
 *   CANCELLED - stock movements are reversed exactly; the document stays for the audit trail
 */
@Service
@RequiredArgsConstructor
public class StoreDocumentService {

    private final StoreDocumentRepository documentRepository;
    private final StockTransactionRepository transactionRepository;
    private final StoreLocationRepository locationRepository;
    private final StockService stockService;
    private final DocumentNumberService numberService;
    private final PlantService plantService;
    private final PartyRepository partyRepository;
    private final MachineRepository machineRepository;
    private final MaterialRepository materialRepository;

    @Transactional(readOnly = true)
    public PageResponse<StoreDocumentSummary> search(Long plantId, StoreDocType type, DocStatus status,
                                                     LocalDate from, LocalDate to, String search, int page, int size) {
        PlantAccess.require(plantId);
        String pattern = Text.trimToNull(search) == null ? null : "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        // Newest first; drafts (no number yet) sort by date like the rest.
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Order.desc("docDate"), Sort.Order.desc("id")));
        return PageResponse.of(documentRepository.search(plantId, type, status, from, to, pattern, pageable),
                StoreDocumentSummary::from);
    }

    @Transactional(readOnly = true)
    public StoreDocumentResponse get(Long id) {
        return StoreDocumentResponse.from(find(id));
    }

    @Transactional
    public StoreDocumentResponse create(StoreDocumentRequest req) {
        Plant plant = plantService.find(req.plantId());
        PlantAccess.require(plant.getId());
        StoreDocument doc = new StoreDocument();
        doc.setPlant(plant);
        doc.setDocType(req.docType());
        apply(doc, req);
        return StoreDocumentResponse.from(documentRepository.save(doc));
    }

    @Transactional
    public StoreDocumentResponse update(Long id, StoreDocumentRequest req) {
        StoreDocument doc = find(id);
        requireTypedInStore(doc);
        requireDraft(doc, "edited");
        if (!doc.getPlant().getId().equals(req.plantId()) || doc.getDocType() != req.docType()) {
            throw new BusinessException("A document can't be moved to another plant or type");
        }
        apply(doc, req);
        return StoreDocumentResponse.from(doc);
    }

    @Transactional
    public void deleteDraft(Long id) {
        StoreDocument doc = find(id);
        requireTypedInStore(doc);
        requireDraft(doc, "deleted");
        documentRepository.delete(doc);
    }

    /** Gives the document its number and moves the stock - all in one transaction, all or nothing. */
    @Transactional
    public StoreDocumentResponse post(Long id) {
        StoreDocument doc = find(id);
        requireTypedInStore(doc);
        requireDraft(doc, "posted");
        postDocument(doc);
        return StoreDocumentResponse.from(doc);
    }

    private void postDocument(StoreDocument doc) {
        requireNotFuture(doc.getDocDate());

        doc.setDocNo(numberService.next(doc.getPlant(), doc.getDocType(), doc.getDocDate()));

        // Always lock balance rows in the same order (godown, then material). If two people post
        // documents with the same materials at once, both wait in line instead of deadlocking.
        List<StoreDocumentLine> ordered = doc.getLines().stream()
                .sorted(Comparator.comparing((StoreDocumentLine l) -> l.getLocation().getId())
                        .thenComparing(l -> l.getMaterial().getId()))
                .toList();

        BigDecimal total = BigDecimal.ZERO;
        for (StoreDocumentLine line : ordered) {
            if (line.getQuantity().signum() == 0) {
                continue;  // a GRN line where everything was rejected moves no stock
            }
            boolean issue = doc.getDocType() == StoreDocType.ISSUE;
            BigDecimal movementQty = issue ? line.getQuantity().negate() : line.getQuantity();
            BigDecimal rate = issue || movementQty.signum() < 0 ? null : line.getRate();
            var result = stockService.apply(doc,
                    new StockService.Movement(line.getLocation(), line.getMaterial(), movementQty, rate, null), false);

            // Copy back what the engine used, so issue lines show the value they took out.
            line.setRate(result.rate());
            line.setValue(issue ? result.value().negate() : result.value());
            total = total.add(line.getValue());
        }

        doc.setTotalValue(total);
        doc.setStatus(DocStatus.POSTED);
        doc.setPostedAt(Instant.now());
        doc.setPostedBy(CurrentUser.get().getUsername());
    }

    /**
     * Reverses every stock movement of the document with the SAME quantity and value it moved,
     * so the stock value comes back exactly. Fails if the stock was already used
     * (e.g. cancelling a GRN whose material has been issued).
     */
    @Transactional
    public StoreDocumentResponse cancel(Long id, String reason) {
        StoreDocument doc = find(id);
        requireTypedInStore(doc);
        cancelDocument(doc, reason);
        return StoreDocumentResponse.from(doc);
    }

    private void cancelDocument(StoreDocument doc, String reason) {
        if (doc.getStatus() != DocStatus.POSTED) {
            throw new BusinessException("Only a posted document can be cancelled");
        }

        List<StockTransaction> movements = transactionRepository.findByDocumentIdAndReversalFalseOrderByIdAsc(doc.getId())
                .stream()
                .sorted(Comparator.comparing((StockTransaction t) -> t.getLocation().getId())
                        .thenComparing(t -> t.getMaterial().getId()))
                .toList();
        try {
            for (StockTransaction t : movements) {
                stockService.apply(doc, new StockService.Movement(t.getLocation(), t.getMaterial(),
                        t.getQuantity().negate(), t.getRate(), t.getValue().negate()), true);
            }
        } catch (BusinessException e) {
            throw new BusinessException("Can't cancel " + doc.getDocNo() + ". " + e.getMessage());
        }

        doc.setStatus(DocStatus.CANCELLED);
        doc.setCancelledAt(Instant.now());
        doc.setCancelledBy(CurrentUser.get().getUsername());
        doc.setCancelReason(reason.trim());
    }

    // ------------------------------------------------------------------ for other modules

    /**
     * Makes and posts an ISSUE for another module, e.g. the raw material behind a ball mill charge.
     * Runs inside the caller's transaction (MANDATORY): if the caller fails later, the issue and its
     * stock movement are rolled back with it. The caller must already have checked plant access.
     *
     * @param origin who made it ("BODY_BATCH"); the store screens then refuse to edit or cancel it
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public StoreDocument postIssueFor(String origin, StoreDocumentRequest req) {
        StoreDocument doc = new StoreDocument();
        doc.setPlant(plantService.find(req.plantId()));
        doc.setDocType(StoreDocType.ISSUE);
        doc.setOrigin(origin);
        apply(doc, req);
        documentRepository.save(doc);
        postDocument(doc);
        return doc;
    }

    /** Reverses a document made with {@link #postIssueFor}, inside the caller's transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelFor(StoreDocument doc, String reason) {
        cancelDocument(doc, reason);
    }

    /** Loads the document with its lines and checks plant access. */
    private StoreDocument find(Long id) {
        StoreDocument doc = documentRepository.findWithDetailsById(id)
                .orElseThrow(() -> new NotFoundException("Store document", id));
        PlantAccess.require(doc.getPlant().getId());
        return doc;
    }

    /** An issue made by production belongs to production: change or cancel it there, not here. */
    private static void requireTypedInStore(StoreDocument doc) {
        if (doc.getOrigin() != null) {
            throw new BusinessException(doc.getDocNo() + " was made automatically (" + doc.getIssuedTo()
                    + "). Change or cancel it from Production.");
        }
    }

    private static void requireDraft(StoreDocument doc, String action) {
        if (doc.getStatus() != DocStatus.DRAFT) {
            throw new BusinessException("Only a draft can be " + action + ". " + doc.getDocNo() + " is "
                    + doc.getStatus().name().toLowerCase(Locale.ROOT));
        }
    }

    private static void requireNotFuture(LocalDate date) {
        if (date.isAfter(LocalDate.now())) {
            throw new BusinessException("The date can't be in the future");
        }
    }

    // ------------------------------------------------------------------ saving a draft

    private void apply(StoreDocument doc, StoreDocumentRequest req) {
        requireNotFuture(req.docDate());
        doc.setDocDate(req.docDate());
        doc.setRemarks(Text.trimToNull(req.remarks()));

        // Clear every type-specific field first, then fill only the ones this type uses.
        Party oldSupplier = doc.getSupplier();
        Machine oldMachine = doc.getMachine();
        doc.setSupplier(null);
        doc.setSupplierInvoiceNo(null);
        doc.setSupplierInvoiceDate(null);
        doc.setVehicleNo(null);
        doc.setWeighbridgeSlipNo(null);
        doc.setMachine(null);
        doc.setIssuedTo(null);
        doc.setReason(null);

        switch (doc.getDocType()) {
            case GRN -> {
                doc.setSupplier(chooseSupplier(req.supplierId(), oldSupplier));
                doc.setSupplierInvoiceNo(Text.upperOrNull(req.supplierInvoiceNo()));
                if (req.supplierInvoiceDate() != null && req.supplierInvoiceDate().isAfter(req.docDate())) {
                    throw new BusinessException("The supplier's invoice date can't be after the GRN date");
                }
                doc.setSupplierInvoiceDate(req.supplierInvoiceDate());
                doc.setVehicleNo(vehicleNo(req.vehicleNo()));
                doc.setWeighbridgeSlipNo(Text.trimToNull(req.weighbridgeSlipNo()));
            }
            case ISSUE -> {
                doc.setMachine(chooseMachine(req.machineId(), doc.getPlant(), oldMachine));
                doc.setIssuedTo(Text.trimToNull(req.issuedTo()));
            }
            case ADJUSTMENT -> {
                if (req.reason() == null) {
                    throw new BusinessException("Select the reason for the adjustment");
                }
                doc.setReason(req.reason());
            }
        }

        applyLines(doc, req.lines());
    }

    /**
     * Replaces all lines. Clearing the list and adding new ones is simplest: orphanRemoval deletes
     * the old rows. Materials / godowns already on the document may stay even if they became inactive.
     */
    private void applyLines(StoreDocument doc, List<StoreDocumentRequest.Line> reqLines) {
        Set<Long> oldMaterials = doc.getLines().stream().map(l -> l.getMaterial().getId()).collect(Collectors.toSet());
        Set<Long> oldLocations = doc.getLines().stream().map(l -> l.getLocation().getId()).collect(Collectors.toSet());

        // Two queries for all lines, instead of two per line.
        Map<Long, Material> materials = byId(materialRepository.findAllById(
                reqLines.stream().map(StoreDocumentRequest.Line::materialId).collect(Collectors.toSet())));
        Map<Long, StoreLocation> locations = byId(locationRepository.findAllById(
                reqLines.stream().map(StoreDocumentRequest.Line::locationId).collect(Collectors.toSet())));

        doc.getLines().clear();
        BigDecimal total = BigDecimal.ZERO;
        int lineNo = 0;
        for (StoreDocumentRequest.Line r : reqLines) {
            lineNo++;
            String at = "Line " + lineNo + ": ";

            Material material = materials.get(r.materialId());
            if (material == null) {
                throw new NotFoundException("Material", r.materialId());
            }
            if (!material.isActive() && !oldMaterials.contains(material.getId())) {
                throw new BusinessException(at + "material " + material.getCode() + " is inactive");
            }
            StoreLocation location = locations.get(r.locationId());
            if (location == null || !location.getPlant().getId().equals(doc.getPlant().getId())) {
                throw new BusinessException(at + "choose a godown of plant " + doc.getPlant().getCode());
            }
            if (!location.isActive() && !oldLocations.contains(location.getId())) {
                throw new BusinessException(at + "godown " + location.getCode() + " is inactive");
            }

            StoreDocumentLine line = new StoreDocumentLine();
            line.setDocument(doc);
            line.setLineNo(lineNo);
            line.setMaterial(material);
            line.setLocation(location);
            line.setRemarks(Text.trimToNull(r.remarks()));
            fillQuantities(doc, line, r, material, at);

            doc.getLines().add(line);
            if (line.getValue() != null) {
                total = total.add(line.getValue());
            }
        }
        // For a GRN this is the final total; issues get their value when posted (at the average rate).
        doc.setTotalValue(total);
    }

    /** The quantity rules differ by document type. */
    private static void fillQuantities(StoreDocument doc, StoreDocumentLine line, StoreDocumentRequest.Line r,
                                       Material material, String at) {
        int decimals = material.getUnit().getDecimals();
        String unit = material.getUnit().getCode();
        switch (doc.getDocType()) {
            case GRN -> {
                BigDecimal received = r.receivedQty();
                BigDecimal rejected = r.rejectedQty() == null ? BigDecimal.ZERO : r.rejectedQty();
                if (received == null || received.signum() <= 0) {
                    throw new BusinessException(at + "enter the received quantity");
                }
                if (rejected.compareTo(received) > 0) {
                    throw new BusinessException(at + "rejected can't be more than received");
                }
                if (r.rate() == null) {
                    throw new BusinessException(at + "enter the rate");
                }
                requireDecimals(received, decimals, unit, at);
                requireDecimals(rejected, decimals, unit, at);
                BigDecimal accepted = received.subtract(rejected);
                line.setReceivedQty(received);
                line.setRejectedQty(rejected);
                line.setQuantity(accepted);
                line.setRate(r.rate());
                line.setValue(accepted.multiply(r.rate()).setScale(2, RoundingMode.HALF_UP));
                line.setMoisturePct(r.moisturePct());
            }
            case ISSUE -> {
                if (r.quantity() == null || r.quantity().signum() <= 0) {
                    throw new BusinessException(at + "enter the quantity to issue");
                }
                requireDecimals(r.quantity(), decimals, unit, at);
                line.setQuantity(r.quantity());
            }
            case ADJUSTMENT -> {
                if (r.quantity() == null || r.quantity().signum() == 0) {
                    throw new BusinessException(at + "enter the quantity (+ to add, - to reduce)");
                }
                requireDecimals(r.quantity(), decimals, unit, at);
                boolean opening = doc.getReason() == AdjustmentReason.OPENING;
                if (opening && (r.quantity().signum() < 0 || r.rate() == null)) {
                    throw new BusinessException(at + "opening stock needs a positive quantity and a rate");
                }
                line.setQuantity(r.quantity());
                // A rate only makes sense when adding stock. Reductions always go out at the average.
                if (r.quantity().signum() > 0 && r.rate() != null) {
                    line.setRate(r.rate());
                    line.setValue(r.quantity().multiply(r.rate()).setScale(2, RoundingMode.HALF_UP));
                }
            }
        }
    }

    /** 10.500 KG is fine (3 decimals allowed), 10.5 BAG is not (0 decimals). */
    private static void requireDecimals(BigDecimal qty, int decimals, String unit, String at) {
        if (qty.stripTrailingZeros().scale() > decimals) {
            throw new BusinessException(at + "quantity in " + unit + " can have at most " + decimals + " decimal places");
        }
    }

    private Party chooseSupplier(Long supplierId, Party current) {
        if (supplierId == null) {
            throw new BusinessException("Select the supplier");
        }
        Party party = partyRepository.findById(supplierId).orElseThrow(() -> new NotFoundException("Party", supplierId));
        if (party.getPartyType() != PartyType.SUPPLIER) {
            throw new BusinessException(party.getName() + " is not a supplier");
        }
        requireActiveIfChanged(party.isActive(), current, party, "Supplier " + party.getCode());
        return party;
    }

    /** Optional: material can be issued to a department or person instead of a machine. */
    private Machine chooseMachine(Long machineId, Plant plant, Machine current) {
        if (machineId == null) {
            return null;
        }
        Machine machine = machineRepository.findWithPlantById(machineId)
                .orElseThrow(() -> new NotFoundException("Machine", machineId));
        if (!machine.getPlant().getId().equals(plant.getId())) {
            throw new BusinessException("Machine " + machine.getCode() + " is not in plant " + plant.getCode());
        }
        requireActiveIfChanged(machine.isActive(), current, machine, "Machine " + machine.getCode());
        return machine;
    }

    /** Same rule as the masters: an inactive record may stay, but can't be newly picked. */
    private static void requireActiveIfChanged(boolean active, BaseEntity current, BaseEntity chosen, String what) {
        boolean unchanged = current != null && current.getId().equals(chosen.getId());
        if (!active && !unchanged) {
            throw new BusinessException(what + " is inactive. Choose an active one.");
        }
    }

    /** "gj 36 t 1234" -> "GJ36T1234": one spelling, so searching by vehicle works. */
    private static String vehicleNo(String raw) {
        String v = Text.upperOrNull(raw);
        return v == null ? null : v.replaceAll("[\\s-]", "");
    }

    private static <T extends BaseEntity> Map<Long, T> byId(List<T> items) {
        return items.stream().collect(Collectors.toMap(BaseEntity::getId, Function.identity()));
    }
}
