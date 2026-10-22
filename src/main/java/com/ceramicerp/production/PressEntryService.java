package com.ceramicerp.production;

import com.ceramicerp.common.PageResponse;
import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.master.MachineRepository;
import com.ceramicerp.master.MachineType;
import com.ceramicerp.master.Shift;
import com.ceramicerp.master.ShiftService;
import com.ceramicerp.numbering.DocumentNumberService;
import com.ceramicerp.organization.Plant;
import com.ceramicerp.organization.PlantService;
import com.ceramicerp.production.dto.PressEntryRequest;
import com.ceramicerp.production.dto.PressEntryResponse;
import com.ceramicerp.production.dto.PressEntrySummary;
import com.ceramicerp.security.CurrentUser;
import com.ceramicerp.security.PlantAccess;
import com.ceramicerp.store.DocStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static com.ceramicerp.production.ProductionRules.requireActiveIfChanged;
import static com.ceramicerp.production.ProductionRules.requireMachine;
import static com.ceramicerp.production.ProductionRules.requireNotFuture;
import static com.ceramicerp.production.ProductionRules.sqm;
import static com.ceramicerp.production.ProductionRules.sqmPerTile;

/**
 * Press & dryer shift entries. Posting draws the powder from the silo, OLDEST spray run first (FIFO),
 * and records which runs it came from - that is what lets a lot be traced back to its body batches.
 */
@Service
@RequiredArgsConstructor
public class PressEntryService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final PressEntryRepository entryRepository;
    private final ProductionLotRepository lotRepository;
    private final SprayRunRepository runRepository;
    private final MachineRepository machineRepository;
    private final PlantService plantService;
    private final ShiftService shiftService;
    private final DocumentNumberService numberService;

    @Transactional(readOnly = true)
    public PageResponse<PressEntrySummary> search(Long plantId, DocStatus status, LocalDate from, LocalDate to,
                                                  String search, int page, int size) {
        PlantAccess.require(plantId);
        return PageResponse.of(entryRepository.search(plantId, status, from, to, ProductionRules.pattern(search),
                ProductionRules.page(page, size)), PressEntrySummary::from);
    }

    @Transactional(readOnly = true)
    public PressEntryResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public PressEntryResponse create(PressEntryRequest req) {
        Plant plant = plantService.find(req.plantId());
        PlantAccess.require(plant.getId());
        PressEntry entry = new PressEntry();
        entry.setPlant(plant);
        apply(entry, req);
        return toResponse(entryRepository.save(entry));
    }

    @Transactional
    public PressEntryResponse update(Long id, PressEntryRequest req) {
        PressEntry entry = find(id);
        requireDraft(entry, "edited");
        if (!entry.getPlant().getId().equals(req.plantId())) {
            throw new BusinessException("An entry can't be moved to another plant");
        }
        apply(entry, req);
        return toResponse(entry);
    }

    @Transactional
    public void deleteDraft(Long id) {
        PressEntry entry = find(id);
        requireDraft(entry, "deleted");
        entryRepository.delete(entry);
    }

    @Transactional
    public PressEntryResponse post(Long id) {
        PressEntry entry = find(id);
        requireDraft(entry, "posted");
        requireLotUsable(entry.getLot());
        requireNotFuture(entry.getProdDate());

        entry.setEntryNo(numberService.next(entry.getPlant(), ProductionDocType.PRESS_ENTRY, entry.getProdDate()));
        drawPowder(entry);
        entry.setStatus(DocStatus.POSTED);
        entry.setPostedAt(Instant.now());
        entry.setPostedBy(CurrentUser.get().getUsername());
        return toResponse(entry);
    }

    /** The powder goes back to the runs it came from; the entry stays for the record. */
    @Transactional
    public PressEntryResponse cancel(Long id, String reason) {
        PressEntry entry = find(id);
        if (entry.getStatus() != DocStatus.POSTED) {
            throw new BusinessException("Only a posted entry can be cancelled");
        }
        for (PressEntryPowder p : entry.getPowders()) {
            SprayRun run = p.getSprayRun();
            // min(...): if the silo was marked empty meanwhile, never put back more than the run made
            run.setPowderLeft(run.getPowderLeft().add(p.getTonnes()).min(run.getPowderTon()));
        }
        entry.setStatus(DocStatus.CANCELLED);
        entry.setCancelledAt(Instant.now());
        entry.setCancelledBy(CurrentUser.get().getUsername());
        entry.setCancelReason(reason.trim());
        return toResponse(entry);
    }

    /**
     * FIFO: take from the oldest run in the silo until the entry's tonnes are covered.
     * 42 T from a silo holding [SD/1: 30 T, SD/2: 30 T] -> 30 T from SD/1, 12 T from SD/2.
     * If the silo had less on record than was really pressed, the rest is "untraced" - production is
     * never blocked by a record that was out of date.
     */
    private void drawPowder(PressEntry entry) {
        BigDecimal remaining = entry.getPowderTon();
        for (SprayRun run : runRepository.findForDraw(entry.getSilo().getId())) {
            if (remaining.signum() == 0) {
                break;
            }
            BigDecimal take = remaining.min(run.getPowderLeft());
            run.setPowderLeft(run.getPowderLeft().subtract(take));
            PressEntryPowder link = new PressEntryPowder();
            link.setEntry(entry);
            link.setSprayRun(run);
            link.setTonnes(take);
            entry.getPowders().add(link);
            remaining = remaining.subtract(take);
        }
        entry.setUntracedTon(remaining);
    }

    private PressEntry find(Long id) {
        PressEntry entry = entryRepository.findWithDetailsById(id).orElseThrow(() -> new NotFoundException("Press entry", id));
        PlantAccess.require(entry.getPlant().getId());
        return entry;
    }

    private static void requireDraft(PressEntry entry, String action) {
        if (entry.getStatus() != DocStatus.DRAFT) {
            throw new BusinessException("Only a draft can be " + action + ". " + entry.getEntryNo() + " is "
                    + entry.getStatus().name().toLowerCase(Locale.ROOT));
        }
    }

    private static void requireLotUsable(ProductionLot lot) {
        if (lot.getStatus() == LotStatus.CANCELLED) {
            throw new BusinessException("Lot " + lot.getLotNo() + " is cancelled");
        }
    }

    private PressEntryResponse toResponse(PressEntry e) {
        BigDecimal perTile = sqmPerTile(e.getLot().getDesign().getTileSize());
        return PressEntryResponse.from(e, perTile, sqm(e.goodCount(), perTile));
    }

    private void apply(PressEntry entry, PressEntryRequest req) {
        ProductionLot lot = lotRepository.findWithDetailsById(req.lotId())
                .filter(l -> l.getPlant().getId().equals(entry.getPlant().getId()))
                .orElseThrow(() -> new NotFoundException("Design lot", req.lotId()));
        requireLotUsable(lot);
        requireNotFuture(req.prodDate());
        if (req.prodDate().isBefore(lot.getOpenedDate())) {
            throw new BusinessException("The date can't be before the lot started (" + lot.getOpenedDate().format(DAY) + ")");
        }
        entry.setLot(lot);
        entry.setPress(lot.getPress());
        entry.setProdDate(req.prodDate());

        Shift shift = shiftService.find(req.shiftId());
        requireActiveIfChanged(shift.isActive(), entry.getShift(), shift, "Shift " + shift.getCode());
        entry.setShift(shift);
        entry.setDryer(req.dryerId() == null ? null
                : requireMachine(machineRepository, req.dryerId(), entry.getPlant(), MachineType.DRYER, entry.getDryer()));
        entry.setSilo(requireMachine(machineRepository, req.siloId(), entry.getPlant(), MachineType.SILO, entry.getSilo()));

        int pressRejects = req.pressRejects() == null ? 0 : req.pressRejects();
        int dryerRejects = req.dryerRejects() == null ? 0 : req.dryerRejects();
        if (pressRejects + dryerRejects > req.pressedCount()) {
            throw new BusinessException("Rejects can't be more than the tiles pressed");
        }
        entry.setPressedCount(req.pressedCount());
        entry.setPressRejects(pressRejects);
        entry.setDryerRejects(dryerRejects);

        // Powder used: typed in, or tiles pressed x green weight (2,000 tiles x 21 kg = 42 T).
        entry.setGreenWeightKg(req.greenWeightKg());
        if (req.powderTon() != null) {
            entry.setPowderTon(req.powderTon());
        } else if (req.greenWeightKg() != null) {
            entry.setPowderTon(req.greenWeightKg().multiply(BigDecimal.valueOf(req.pressedCount()))
                    .divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP));
        } else {
            throw new BusinessException("Enter the green tile weight or the powder used");
        }

        entry.setPressure(req.pressure());
        entry.setCyclesPerMin(req.cyclesPerMin());
        entry.setThicknessMm(req.thicknessMm());
        entry.setDryerTemp(req.dryerTemp());
        entry.setDryerMoisture(req.dryerMoisture());
        entry.setRemarks(Text.trimToNull(req.remarks()));
    }
}
