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
import com.ceramicerp.production.dto.CurrentShiftResponse;
import com.ceramicerp.production.dto.LineEntryRequest;
import com.ceramicerp.production.dto.LineEntryResponse;
import com.ceramicerp.production.dto.LineEntrySummary;
import com.ceramicerp.production.dto.LineOverview;
import com.ceramicerp.production.dto.LotFlow;
import com.ceramicerp.production.dto.LotSummary;
import com.ceramicerp.production.dto.LotTotals;
import com.ceramicerp.security.CurrentUser;
import com.ceramicerp.security.PlantAccess;
import com.ceramicerp.store.DocStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.ceramicerp.production.ProductionRules.requireActiveIfChanged;
import static com.ceramicerp.production.ProductionRules.requireMachine;
import static com.ceramicerp.production.ProductionRules.requireNotFuture;
import static com.ceramicerp.production.ProductionRules.sqm;
import static com.ceramicerp.production.ProductionRules.sqmPerTile;

/**
 * Glaze & print and kiln shift entries (one table, a "stage" column).
 * These stages move no stock and draw no powder: posting gives the number and makes the
 * entry count in the lot's funnel.
 */
@Service
@RequiredArgsConstructor
public class LineEntryService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    /** Lots closed this recently still show on the glaze & kiln screen: their tiles may still be in the line. */
    private static final Duration DRAIN_WINDOW = Duration.ofDays(3);

    private final LineEntryRepository entryRepository;
    private final ProductionLotRepository lotRepository;
    private final MachineRepository machineRepository;
    private final PlantService plantService;
    private final ShiftService shiftService;
    private final DocumentNumberService numberService;
    private final LotService lotService;
    private final BodyPrepService bodyPrepService;

    @Transactional(readOnly = true)
    public PageResponse<LineEntrySummary> search(Long plantId, LineStage stage, DocStatus status, LocalDate from,
                                                 LocalDate to, String search, int page, int size) {
        PlantAccess.require(plantId);
        return PageResponse.of(entryRepository.search(plantId, stage, status, from, to, ProductionRules.pattern(search),
                ProductionRules.page(page, size)), LineEntrySummary::from);
    }

    @Transactional(readOnly = true)
    public LineEntryResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public LineEntryResponse create(LineEntryRequest req) {
        requireStagePermission(req.stage());
        Plant plant = plantService.find(req.plantId());
        PlantAccess.require(plant.getId());
        LineEntry entry = new LineEntry();
        entry.setPlant(plant);
        entry.setStage(req.stage());
        apply(entry, req);
        return toResponse(entryRepository.save(entry));
    }

    @Transactional
    public LineEntryResponse update(Long id, LineEntryRequest req) {
        LineEntry entry = find(id);
        requireStagePermission(entry.getStage());
        requireDraft(entry, "edited");
        if (!entry.getPlant().getId().equals(req.plantId()) || entry.getStage() != req.stage()) {
            throw new BusinessException("An entry can't be moved to another plant or stage");
        }
        apply(entry, req);
        return toResponse(entry);
    }

    @Transactional
    public void deleteDraft(Long id) {
        LineEntry entry = find(id);
        requireStagePermission(entry.getStage());
        requireDraft(entry, "deleted");
        entryRepository.delete(entry);
    }

    @Transactional
    public LineEntryResponse post(Long id) {
        LineEntry entry = find(id);
        requireStagePermission(entry.getStage());
        requireDraft(entry, "posted");
        requireLotUsable(entry.getLot());
        requireNotFuture(entry.getProdDate());
        entry.setEntryNo(numberService.next(entry.getPlant(), entry.getStage().docType(), entry.getProdDate()));
        entry.setStatus(DocStatus.POSTED);
        entry.setPostedAt(Instant.now());
        entry.setPostedBy(CurrentUser.get().getUsername());
        return toResponse(entry);
    }

    /** Needs PRODUCTION_CANCEL (checked by the controller), whatever the stage. */
    @Transactional
    public LineEntryResponse cancel(Long id, String reason) {
        LineEntry entry = find(id);
        if (entry.getStatus() != DocStatus.POSTED) {
            throw new BusinessException("Only a posted entry can be cancelled");
        }
        entry.setStatus(DocStatus.CANCELLED);
        entry.setCancelledAt(Instant.now());
        entry.setCancelledBy(CurrentUser.get().getUsername());
        entry.setCancelReason(reason.trim());
        return toResponse(entry);
    }

    /** Lots whose tiles may be in the line now, each with its funnel, plus today's glaze and kiln figures. */
    @Transactional(readOnly = true)
    public LineOverview overview(Long plantId) {
        PlantAccess.require(plantId);
        CurrentShiftResponse now = bodyPrepService.currentShift();

        List<ProductionLot> lots = lotRepository.findSelectable(plantId, Instant.now().minus(DRAIN_WINDOW));
        Map<Long, LotTotals> press = lotService.pressTotals(lots);
        Map<Long, LotFlow> flows = lotService.flows(lots, press);
        List<LotSummary> summaries = lotService.summaries(lots);
        List<LineOverview.LotLine> lines = summaries.stream()
                .map(s -> new LineOverview.LotLine(s, flows.get(s.id())))
                // a closed lot only matters while some of its tiles are still waiting in the line
                .filter(l -> l.lot().status() == LotStatus.OPEN || l.flow().waitingGlaze() > 0 || l.flow().waitingKiln() > 0)
                .toList();

        long glazeIn = 0;
        long glazeRejects = 0;
        long kilnIn = 0;
        long kilnRejects = 0;
        BigDecimal firedSqm = BigDecimal.ZERO;
        BigDecimal gas = BigDecimal.ZERO;
        for (LineEntry e : entryRepository.findByPlantIdAndProdDateAndStatus(plantId, now.prodDate(), DocStatus.POSTED)) {
            if (e.getStage() == LineStage.GLAZE) {
                glazeIn += e.getTilesIn();
                glazeRejects += e.getRejects();
            } else {
                kilnIn += e.getTilesIn();
                kilnRejects += e.getRejects();
                BigDecimal sqm = sqm(e.tilesOut(), sqmPerTile(e.getLot().getDesign().getTileSize()));
                firedSqm = sqm == null ? firedSqm : firedSqm.add(sqm);
                gas = e.getGasScm() == null ? gas : gas.add(e.getGasScm());
            }
        }
        return new LineOverview(now.prodDate(), now.shiftId(), now.code(), now.name(), lines,
                new LineOverview.Day(glazeIn, glazeRejects, kilnIn, kilnRejects, firedSqm, gas, perSqm(gas, firedSqm)));
    }

    /**
     * The controller lets in anyone with GLAZE_ENTRY or KILN_ENTRY; this checks the one this stage needs.
     * (Method security can't see the stage inside the request body - the service can.)
     */
    private static void requireStagePermission(LineStage stage) {
        if (!CurrentUser.get().getPermissions().contains(stage.permission())) {
            throw new AccessDeniedException("Needs " + stage.permission());
        }
    }

    private LineEntry find(Long id) {
        LineEntry entry = entryRepository.findWithDetailsById(id).orElseThrow(() -> new NotFoundException("Line entry", id));
        PlantAccess.require(entry.getPlant().getId());
        return entry;
    }

    private static void requireDraft(LineEntry entry, String action) {
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

    /** Gas per square metre, or null when nothing was fired. */
    private static BigDecimal perSqm(BigDecimal gas, BigDecimal sqm) {
        if (gas == null || sqm == null || sqm.signum() == 0) {
            return null;
        }
        return gas.divide(sqm, 3, RoundingMode.HALF_UP);
    }

    private LineEntryResponse toResponse(LineEntry e) {
        BigDecimal perTile = sqmPerTile(e.getLot().getDesign().getTileSize());
        BigDecimal sqmOut = sqm(e.tilesOut(), perTile);
        return LineEntryResponse.from(e, perTile, sqmOut, e.getStage() == LineStage.KILN ? perSqm(e.getGasScm(), sqmOut) : null);
    }

    private void apply(LineEntry entry, LineEntryRequest req) {
        ProductionLot lot = lotRepository.findWithDetailsById(req.lotId())
                .filter(l -> l.getPlant().getId().equals(entry.getPlant().getId()))
                .orElseThrow(() -> new NotFoundException("Design lot", req.lotId()));
        requireLotUsable(lot);
        requireNotFuture(req.prodDate());
        if (req.prodDate().isBefore(lot.getOpenedDate())) {
            throw new BusinessException("The date can't be before the lot started (" + lot.getOpenedDate().format(DAY) + ")");
        }
        entry.setLot(lot);
        entry.setProdDate(req.prodDate());

        Shift shift = shiftService.find(req.shiftId());
        requireActiveIfChanged(shift.isActive(), entry.getShift(), shift, "Shift " + shift.getCode());
        entry.setShift(shift);

        LineStage stage = entry.getStage();
        entry.setMachine(requireMachine(machineRepository, req.machineId(), entry.getPlant(), stage.machineType(), entry.getMachine()));
        entry.setPrinter(stage == LineStage.GLAZE && req.printerId() != null
                ? requireMachine(machineRepository, req.printerId(), entry.getPlant(), MachineType.DIGITAL_PRINTER, entry.getPrinter())
                : null);

        int rejects = req.rejects() == null ? 0 : req.rejects();
        if (rejects > req.tilesIn()) {
            throw new BusinessException("Rejects can't be more than the tiles in");
        }
        entry.setTilesIn(req.tilesIn());
        entry.setRejects(rejects);

        // Each stage keeps only its own readings; the other stage's fields are cleared.
        boolean glaze = stage == LineStage.GLAZE;
        entry.setEngobeGpt(glaze ? req.engobeGpt() : null);
        entry.setGlazeGpt(glaze ? req.glazeGpt() : null);
        entry.setGlazeDensity(glaze ? req.glazeDensity() : null);
        entry.setInkLitres(glaze ? req.inkLitres() : null);
        entry.setMaxTemp(glaze ? null : req.maxTemp());
        entry.setCycleMin(glaze ? null : req.cycleMin());
        entry.setGasScm(glaze ? null : req.gasScm());
        entry.setRemarks(Text.trimToNull(req.remarks()));
    }
}
