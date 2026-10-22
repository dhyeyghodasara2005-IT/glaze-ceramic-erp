package com.ceramicerp.production;

import com.ceramicerp.common.BaseEntity;
import com.ceramicerp.common.PageResponse;
import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.master.BodyRecipe;
import com.ceramicerp.master.BodyRecipeService;
import com.ceramicerp.master.Machine;
import com.ceramicerp.master.MachineRepository;
import com.ceramicerp.master.MachineType;
import com.ceramicerp.master.Material;
import com.ceramicerp.master.MaterialRepository;
import com.ceramicerp.master.Shift;
import com.ceramicerp.master.ShiftService;
import com.ceramicerp.numbering.DocumentNumberService;
import com.ceramicerp.organization.Plant;
import com.ceramicerp.organization.PlantService;
import com.ceramicerp.production.dto.BodyBatchRequest;
import com.ceramicerp.production.dto.BodyBatchResponse;
import com.ceramicerp.production.dto.BodyBatchSummary;
import com.ceramicerp.production.dto.DischargeRequest;
import com.ceramicerp.security.CurrentUser;
import com.ceramicerp.security.PlantAccess;
import com.ceramicerp.store.DocStatus;
import com.ceramicerp.store.StoreDocType;
import com.ceramicerp.store.StoreDocumentService;
import com.ceramicerp.store.StoreLocation;
import com.ceramicerp.store.StoreLocationRepository;
import com.ceramicerp.store.dto.StoreDocumentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.ceramicerp.production.ProductionRules.requireActiveIfChanged;
import static com.ceramicerp.production.ProductionRules.requireMachine;
import static com.ceramicerp.production.ProductionRules.requireNotFuture;

/**
 * Ball mill charges (body batches).
 *
 *   DRAFT --charge--> CHARGED --discharge--> DISCHARGED --spray dryer--> CONSUMED
 *
 * Charging makes the store ISSUE for the raw materials in the same transaction, so the stock
 * and the batch can never disagree: either both happen, or neither.
 */
@Service
@RequiredArgsConstructor
public class BodyBatchService {

    /** Marks the store issues this module makes (store_documents.origin). */
    static final String ORIGIN = "BODY_BATCH";

    private final BodyBatchRepository batchRepository;
    private final SprayRunBatchRepository runBatchRepository;
    private final MachineRepository machineRepository;
    private final MaterialRepository materialRepository;
    private final StoreLocationRepository locationRepository;
    private final PlantService plantService;
    private final ShiftService shiftService;
    private final BodyRecipeService recipeService;
    private final DocumentNumberService numberService;
    private final StoreDocumentService storeDocumentService;

    @Transactional(readOnly = true)
    public PageResponse<BodyBatchSummary> search(Long plantId, BodyBatchStatus status, LocalDate from, LocalDate to,
                                                 String search, int page, int size) {
        PlantAccess.require(plantId);
        return PageResponse.of(batchRepository.search(plantId, status, from, to, ProductionRules.pattern(search),
                ProductionRules.page(page, size)), BodyBatchSummary::from);
    }

    @Transactional(readOnly = true)
    public BodyBatchResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public BodyBatchResponse create(BodyBatchRequest req) {
        Plant plant = plantService.find(req.plantId());
        PlantAccess.require(plant.getId());
        BodyBatch batch = new BodyBatch();
        batch.setPlant(plant);
        apply(batch, req);
        return toResponse(batchRepository.save(batch));
    }

    @Transactional
    public BodyBatchResponse update(Long id, BodyBatchRequest req) {
        BodyBatch batch = find(id);
        requireStatus(batch, "edited", BodyBatchStatus.DRAFT);
        if (!batch.getPlant().getId().equals(req.plantId())) {
            throw new BusinessException("A batch can't be moved to another plant");
        }
        apply(batch, req);
        return toResponse(batch);
    }

    @Transactional
    public void deleteDraft(Long id) {
        BodyBatch batch = find(id);
        requireStatus(batch, "deleted", BodyBatchStatus.DRAFT);
        batchRepository.delete(batch);
    }

    /** The mill is loaded: the batch gets its number and the raw materials leave the store. */
    @Transactional
    public BodyBatchResponse charge(Long id) {
        BodyBatch batch = find(id);
        requireStatus(batch, "charged", BodyBatchStatus.DRAFT);
        requireNotFuture(batch.getProdDate());

        // A ball mill holds one charge at a time.
        batchRepository.findFirstByBallMillIdAndStatusAndIdNot(batch.getBallMill().getId(), BodyBatchStatus.CHARGED, id)
                .ifPresent(other -> {
                    throw new BusinessException("Ball mill " + batch.getBallMill().getCode() + " still holds "
                            + other.getBatchNo() + ". Discharge it first.");
                });

        batch.setBatchNo(numberService.next(batch.getPlant(), ProductionDocType.BODY_BATCH, batch.getProdDate()));
        // If the store refuses (not enough clay), its exception rolls back the number and everything else.
        batch.setIssueDocument(storeDocumentService.postIssueFor(ORIGIN, issueFor(batch)));
        batch.setStatus(BodyBatchStatus.CHARGED);
        batch.setChargedAt(Instant.now());
        batch.setChargedBy(CurrentUser.get().getUsername());
        return toResponse(batch);
    }

    /** Milling finished: record the slip test and the tank. Can be repeated to correct results. */
    @Transactional
    public BodyBatchResponse discharge(Long id, DischargeRequest req) {
        BodyBatch batch = find(id);
        requireStatus(batch, "discharged", BodyBatchStatus.CHARGED, BodyBatchStatus.DISCHARGED);
        ProductionRules.requireNotFuture(req.millingEnd(), "The milling end");
        ProductionRules.requireOrder(batch.getMillingStart(), req.millingEnd(), "The milling end");

        batch.setMillingEnd(req.millingEnd());
        batch.setSlipTank(requireMachine(machineRepository, req.slipTankId(), batch.getPlant(),
                MachineType.SLIP_TANK, batch.getSlipTank()));
        batch.setSlipDensity(req.slipDensity());
        batch.setSlipViscosity(req.slipViscosity());
        batch.setSlipResidue(req.slipResidue());
        if (batch.getStatus() == BodyBatchStatus.CHARGED) {
            batch.setStatus(BodyBatchStatus.DISCHARGED);
            batch.setDischargedAt(Instant.now());
            batch.setDischargedBy(CurrentUser.get().getUsername());
        }
        return toResponse(batch);
    }

    /** Reverses the store issue (the raw material goes back to stock) and marks the batch cancelled. */
    @Transactional
    public BodyBatchResponse cancel(Long id, String reason) {
        BodyBatch batch = find(id);
        if (batch.getStatus() == BodyBatchStatus.CONSUMED) {
            throw new BusinessException(batch.getBatchNo() + " was used up by the spray dryer. Cancel those runs first.");
        }
        requireStatus(batch, "cancelled", BodyBatchStatus.CHARGED, BodyBatchStatus.DISCHARGED);
        // Partly sprayed? Then its powder is already in a silo - undo the run first, or the trace breaks.
        List<SprayRunBatch> runs = runBatchRepository.findForBatch(id, DocStatus.POSTED);
        if (!runs.isEmpty()) {
            throw new BusinessException(batch.getBatchNo() + " is used in spray dryer run "
                    + runs.get(0).getRun().getRunNo() + ". Cancel that run first.");
        }

        storeDocumentService.cancelFor(batch.getIssueDocument(), "Body batch " + batch.getBatchNo() + " cancelled: " + reason.trim());
        batch.setStatus(BodyBatchStatus.CANCELLED);
        batch.setCancelledAt(Instant.now());
        batch.setCancelledBy(CurrentUser.get().getUsername());
        batch.setCancelReason(reason.trim());
        return toResponse(batch);
    }

    /** Loads the batch with everything its screen shows and checks plant access. */
    BodyBatch find(Long id) {
        BodyBatch batch = batchRepository.findWithDetailsById(id).orElseThrow(() -> new NotFoundException("Body batch", id));
        PlantAccess.require(batch.getPlant().getId());
        return batch;
    }

    private BodyBatchResponse toResponse(BodyBatch batch) {
        List<SprayRunBatch> runs = batch.getId() == null ? List.of() : runBatchRepository.findForBatch(batch.getId(), DocStatus.POSTED);
        return BodyBatchResponse.from(batch, runs);
    }

    private static void requireStatus(BodyBatch batch, String action, BodyBatchStatus... allowed) {
        for (BodyBatchStatus s : allowed) {
            if (batch.getStatus() == s) {
                return;
            }
        }
        String name = batch.getBatchNo() != null ? batch.getBatchNo() : "This batch";
        throw new BusinessException(name + " can't be " + action + ": it is "
                + batch.getStatus().name().toLowerCase(Locale.ROOT));
    }

    /** The store issue behind the charge: same date, same materials and godowns, issued to the ball mill. */
    private static StoreDocumentRequest issueFor(BodyBatch batch) {
        List<StoreDocumentRequest.Line> lines = batch.getLines().stream()
                .map(l -> new StoreDocumentRequest.Line(l.getMaterial().getId(), l.getLocation().getId(),
                        l.getQuantity(), null, null, null, null, null))
                .toList();
        return new StoreDocumentRequest(batch.getPlant().getId(), StoreDocType.ISSUE, batch.getProdDate(),
                null, null, null, null, null,
                batch.getBallMill().getId(), "Body batch " + batch.getBatchNo(),
                null, "Ball mill charge " + batch.getBatchNo(), lines);
    }

    // ------------------------------------------------------------------ saving a draft

    private void apply(BodyBatch batch, BodyBatchRequest req) {
        requireNotFuture(req.prodDate());
        ProductionRules.requireNotFuture(req.millingStart(), "The milling start");
        batch.setProdDate(req.prodDate());

        Shift shift = shiftService.find(req.shiftId());
        requireActiveIfChanged(shift.isActive(), batch.getShift(), shift, "Shift " + shift.getCode());
        batch.setShift(shift);

        Machine mill = requireMachine(machineRepository, req.ballMillId(), batch.getPlant(), MachineType.BALL_MILL,
                batch.getBallMill());
        batch.setBallMill(mill);

        BodyRecipe recipe = null;
        if (req.recipeId() != null) {
            recipe = recipeService.find(req.recipeId());
            requireActiveIfChanged(recipe.isActive(), batch.getRecipe(), recipe, "Recipe " + recipe.getCode());
        }
        batch.setRecipe(recipe);

        batch.setChargeTon(req.chargeTon());
        batch.setWaterLitres(req.waterLitres());
        batch.setMillingStart(req.millingStart());
        batch.setRemarks(Text.trimToNull(req.remarks()));
        applyLines(batch, req.lines());
    }

    /** Same idea as store documents: replace all lines; old materials / godowns may stay even if inactive now. */
    private void applyLines(BodyBatch batch, List<BodyBatchRequest.Line> reqLines) {
        Set<Long> oldMaterials = batch.getLines().stream().map(l -> l.getMaterial().getId()).collect(Collectors.toSet());
        Set<Long> oldLocations = batch.getLines().stream().map(l -> l.getLocation().getId()).collect(Collectors.toSet());
        Map<Long, Material> materials = byId(materialRepository.findAllById(
                reqLines.stream().map(BodyBatchRequest.Line::materialId).collect(Collectors.toSet())));
        Map<Long, StoreLocation> locations = byId(locationRepository.findAllById(
                reqLines.stream().map(BodyBatchRequest.Line::locationId).collect(Collectors.toSet())));

        batch.getLines().clear();
        int lineNo = 0;
        for (BodyBatchRequest.Line r : reqLines) {
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
            if (location == null || !location.getPlant().getId().equals(batch.getPlant().getId())) {
                throw new BusinessException(at + "choose a godown of plant " + batch.getPlant().getCode());
            }
            if (!location.isActive() && !oldLocations.contains(location.getId())) {
                throw new BusinessException(at + "godown " + location.getCode() + " is inactive");
            }
            int decimals = material.getUnit().getDecimals();
            if (r.quantity().stripTrailingZeros().scale() > decimals) {
                throw new BusinessException(at + "quantity in " + material.getUnit().getCode() + " can have at most "
                        + decimals + " decimal places");
            }

            BodyBatchLine line = new BodyBatchLine();
            line.setBatch(batch);
            line.setLineNo(lineNo);
            line.setMaterial(material);
            line.setLocation(location);
            line.setQuantity(r.quantity());
            batch.getLines().add(line);
        }
    }

    private static <T extends BaseEntity> Map<Long, T> byId(List<T> items) {
        return items.stream().collect(Collectors.toMap(BaseEntity::getId, Function.identity()));
    }
}
