package com.ceramicerp.production;

import com.ceramicerp.common.PageResponse;
import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.master.Design;
import com.ceramicerp.master.DesignRepository;
import com.ceramicerp.master.Grade;
import com.ceramicerp.master.GradeRepository;
import com.ceramicerp.master.Machine;
import com.ceramicerp.master.MachineReplacingEvent;
import com.ceramicerp.master.MachineRepository;
import com.ceramicerp.master.MachineType;
import com.ceramicerp.master.Shift;
import com.ceramicerp.master.ShiftService;
import com.ceramicerp.numbering.DocumentNumberService;
import com.ceramicerp.organization.Plant;
import com.ceramicerp.organization.PlantService;
import com.ceramicerp.production.dto.LineEntrySummary;
import com.ceramicerp.production.dto.LotCheckResponse;
import com.ceramicerp.production.dto.LotFlow;
import com.ceramicerp.production.dto.LotOpenRequest;
import com.ceramicerp.production.dto.LotResponse;
import com.ceramicerp.production.dto.LotSummary;
import com.ceramicerp.production.dto.PackEntrySummary;
import com.ceramicerp.production.dto.PackTotals;
import com.ceramicerp.production.dto.PackedBatch;
import com.ceramicerp.production.dto.PressEntrySummary;
import com.ceramicerp.production.dto.StageTotals;
import com.ceramicerp.security.CurrentUser;
import com.ceramicerp.security.PlantAccess;
import com.ceramicerp.store.DocStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.ceramicerp.production.ProductionRules.pct;
import static com.ceramicerp.production.ProductionRules.requireActiveIfChanged;
import static com.ceramicerp.production.ProductionRules.requireMachine;
import static com.ceramicerp.production.ProductionRules.requireNotFuture;
import static com.ceramicerp.production.ProductionRules.sqm;
import static com.ceramicerp.production.ProductionRules.sqmPerTile;

/**
 * Design lots. The design goes on at the digital printer, and a printer runs one design at a time, so a printer
 * has at most one OPEN lot; starting a new design closes the previous one. The press entries behind a lot are
 * found by shift (LotPressMatch): green tiles carry no design.
 */
@Service
@RequiredArgsConstructor
public class LotService {

    /** Entries can still be made for a lot closed this recently (the last shift's figures come in late). */
    private static final Duration LATE_ENTRY_WINDOW = Duration.ofDays(3);

    private final ProductionLotRepository lotRepository;
    private final LineEntryRepository lineRepository;
    private final PackEntryRepository packRepository;
    private final LotCheckRepository checkRepository;
    private final GradeRepository gradeRepository;
    private final PressEntryPowderRepository powderRepository;
    private final LotPressMatch pressMatch;
    private final SprayRunRepository runRepository;
    private final DesignRepository designRepository;
    private final MachineRepository machineRepository;
    private final PlantService plantService;
    private final ShiftService shiftService;
    private final DocumentNumberService numberService;

    @Transactional(readOnly = true)
    public PageResponse<LotSummary> search(Long plantId, LotStatus status, LocalDate from, LocalDate to, String search,
                                           int page, int size) {
        PlantAccess.require(plantId);
        Page<ProductionLot> lots = lotRepository.search(plantId, status, from, to, ProductionRules.pattern(search),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Order.desc("openedAt"), Sort.Order.desc("id"))));
        return new PageResponse<>(summaries(lots.getContent()), lots.getNumber(), lots.getSize(),
                lots.getTotalElements(), lots.getTotalPages());
    }

    /** Lots a glaze, kiln, polish or pack entry can be made for: running ones, and ones closed in the last few days. */
    @Transactional(readOnly = true)
    public List<LotSummary> selectable(Long plantId) {
        PlantAccess.require(plantId);
        return summaries(lotRepository.findSelectable(plantId, Instant.now().minus(LATE_ENTRY_WINDOW)));
    }

    @Transactional(readOnly = true)
    public LotResponse get(Long id) {
        return toResponse(find(id));
    }

    /** Start a design on a printer. */
    @Transactional
    public LotResponse open(LotOpenRequest req) {
        Plant plant = plantService.find(req.plantId());
        PlantAccess.require(plant.getId());
        requireNotFuture(req.openedDate());
        Shift shift = shiftService.find(req.shiftId());
        requireActiveIfChanged(shift.isActive(), null, shift, "Shift " + shift.getCode());
        Machine printer = requireMachine(machineRepository, req.printerId(), plant, MachineType.DIGITAL_PRINTER, null);
        Design design = designRepository.findWithDetailsById(req.designId())
                .orElseThrow(() -> new NotFoundException("Design", req.designId()));
        if (!design.isActive()) {
            throw new BusinessException("Design " + design.getCode() + " is inactive");
        }

        // One design per printer: changing the design closes the lot that was running.
        lotRepository.findFirstByPrinterIdAndStatus(printer.getId(), LotStatus.OPEN).ifPresent(current -> {
            if (!req.closeCurrent()) {
                throw new BusinessException(printer.getCode() + " is running " + current.getLotNo() + " ("
                        + current.getDesign().getName() + "). Close it first.");
            }
            closeLot(current);
        });

        ProductionLot lot = new ProductionLot();
        lot.setPlant(plant);
        lot.setLotNo(numberService.next(plant, ProductionDocType.LOT, req.openedDate()));
        lot.setDesign(design);
        lot.setPrinter(printer);
        lot.setOpenedDate(req.openedDate());
        lot.setOpenedShift(shift);
        lot.setRemarks(Text.trimToNull(req.remarks()));
        lot.setOpenedAt(Instant.now());
        lot.setOpenedBy(CurrentUser.get().getUsername());
        return toResponse(lotRepository.save(lot));
    }

    @Transactional
    public LotResponse close(Long id) {
        ProductionLot lot = find(id);
        if (lot.getStatus() != LotStatus.OPEN) {
            throw new BusinessException("Only an open lot can be closed");
        }
        closeLot(lot);
        return toResponse(lot);
    }

    /** For a lot opened by mistake: only while nothing has been posted against it. */
    @Transactional
    public LotResponse cancel(Long id, String reason) {
        ProductionLot lot = find(id);
        if (lot.getStatus() == LotStatus.CANCELLED) {
            throw new BusinessException(lot.getLotNo() + " is already cancelled");
        }
        if (lineRepository.existsByLotIdAndStageAndStatus(id, LineStage.GLAZE, DocStatus.POSTED)) {
            throw new BusinessException(lot.getLotNo() + " has posted glaze entries. Cancel them first.");
        }
        lot.setStatus(LotStatus.CANCELLED);
        lot.setCancelledAt(Instant.now());
        lot.setCancelledBy(CurrentUser.get().getUsername());
        lot.setCancelReason(reason.trim());
        return toResponse(lot);
    }

    ProductionLot find(Long id) {
        ProductionLot lot = lotRepository.findWithDetailsById(id).orElseThrow(() -> new NotFoundException("Design lot", id));
        PlantAccess.require(lot.getPlant().getId());
        return lot;
    }

    private static void closeLot(ProductionLot lot) {
        lot.setStatus(LotStatus.CLOSED);
        lot.setClosedAt(Instant.now());
        lot.setClosedBy(CurrentUser.get().getUsername());
    }

    /** Summaries with what each lot glazed: ONE grouped query for all the lots, not one per lot. */
    List<LotSummary> summaries(List<ProductionLot> lots) {
        if (lots.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> glazed = lineRepository.totalsForLots(lots.stream().map(ProductionLot::getId).toList()).stream()
                .filter(t -> t.stage() == LineStage.GLAZE)
                .collect(Collectors.toMap(StageTotals::lotId, StageTotals::tilesIn));
        return lots.stream().map(l -> toSummary(l, glazed.getOrDefault(l.getId(), 0L))).toList();
    }

    private static LotSummary toSummary(ProductionLot l, long glazed) {
        var design = l.getDesign();
        BigDecimal perTile = sqmPerTile(design.getTileSize());
        return new LotSummary(l.getId(), l.getLotNo(), design.getId(), design.getCode(), design.getName(),
                design.getTileSize().getCode(), design.getFinish().getName(), perTile, design.getTileSize().getPcsPerBox(),
                l.getPrinter().getId(), l.getPrinter().getCode(), l.getStatus(), l.getOpenedDate(),
                l.getOpenedShift().getCode(), l.getOpenedAt(), l.getClosedAt(), glazed, sqm(glazed, perTile));
    }

    private LotResponse toResponse(ProductionLot l) {
        var design = l.getDesign();
        BigDecimal perTile = sqmPerTile(design.getTileSize());
        List<PressEntry> behind = pressMatch.behind(l);
        List<PressEntrySummary> entries = behind.stream().map(PressEntrySummary::from).toList();
        LotFlow flow = flows(List.of(l)).get(l.getId());
        List<LotCheckResponse> checks = checkRepository.findByLotIdOrderByCheckDateAscIdAsc(l.getId()).stream()
                .map(LotCheckResponse::from).toList();
        List<PackEntrySummary> packEntries = packRepository.findByLotIdOrderByProdDateAscIdAsc(l.getId()).stream()
                .map(e -> PackEntrySummary.from(e, sqm(e.getPiecesPacked(), perTile))).toList();
        List<PackedBatch> packed = packRepository.packedBatches(l.getId());
        return new LotResponse(l.getId(), l.getPlant().getId(), l.getPlant().getCode(), l.getLotNo(), design.getId(),
                design.getCode(), design.getName(), design.getTileSize().getCode(), design.getFinish().getName(), perTile,
                l.getPrinter().getId(), l.getPrinter().getCode(), l.getPrinter().getName(), l.getStatus(), l.getOpenedDate(),
                l.getOpenedShift().getCode(), l.getRemarks(), l.getOpenedAt(), l.getOpenedBy(), l.getClosedAt(),
                l.getClosedBy(), l.getCancelledAt(), l.getCancelledBy(), l.getCancelReason(), l.getVersion(), pressWork(behind),
                flow, entries, lineEntries(l.getId(), LineStage.GLAZE), lineEntries(l.getId(), LineStage.KILN), checks,
                lineEntries(l.getId(), LineStage.POLISH), packEntries, packed, quality(checks, packed, flow),
                trace(behind));
    }

    /** The press entries of the lot's shifts, added up. */
    private static LotResponse.PressWork pressWork(List<PressEntry> entries) {
        long pressed = 0;
        long pressRejects = 0;
        long dryerRejects = 0;
        BigDecimal powder = BigDecimal.ZERO;
        BigDecimal untraced = BigDecimal.ZERO;
        for (PressEntry e : entries) {
            pressed += e.getPressedCount();
            pressRejects += e.getPressRejects();
            dryerRejects += e.getDryerRejects();
            powder = powder.add(e.getPowderTon());
            untraced = untraced.add(e.getUntracedTon());
        }
        BigDecimal lossPct = pressed == 0 ? null
                : BigDecimal.valueOf((pressRejects + dryerRejects) * 100).divide(BigDecimal.valueOf(pressed), 2, RoundingMode.HALF_UP);
        return new LotResponse.PressWork(entries.size(), pressed, pressRejects, dryerRejects,
                pressed - pressRejects - dryerRejects, lossPct, powder, untraced);
    }

    /** Expected top-grade share (latest check that gave one) against what sorting found so far. */
    private LotResponse.Quality quality(List<LotCheckResponse> checks, List<PackedBatch> packed, LotFlow flow) {
        Optional<Grade> top = ProductionRules.topGrade(gradeRepository);
        BigDecimal expected = null;
        for (LotCheckResponse c : checks) {     // oldest first: the last one wins
            if (c.status() == DocStatus.POSTED && c.expectedTopPct() != null) {
                expected = c.expectedTopPct();
            }
        }
        BigDecimal actual = top.map(g -> pct(packed.stream().filter(b -> b.gradeId().equals(g.getId()))
                .mapToLong(PackedBatch::pieces).sum(), flow.sorted())).orElse(null);
        return new LotResponse.Quality(top.map(Grade::getName).orElse(null), expected, actual);
    }

    private List<LineEntrySummary> lineEntries(Long lotId, LineStage stage) {
        return lineRepository.findByLotIdAndStageOrderByProdDateAscIdAsc(lotId, stage).stream()
                .map(LineEntrySummary::from).toList();
    }

    /** Funnels for many lots: one grouped query for the line-stage totals of all of them, one for packing. */
    Map<Long, LotFlow> flows(List<ProductionLot> lots) {
        List<Long> ids = lots.stream().map(ProductionLot::getId).toList();
        Map<Long, Map<LineStage, StageTotals>> byLot = new HashMap<>();
        Map<Long, PackTotals> packs = new HashMap<>();
        if (!ids.isEmpty()) {
            for (StageTotals s : lineRepository.totalsForLots(ids)) {
                byLot.computeIfAbsent(s.lotId(), k -> new EnumMap<>(LineStage.class)).put(s.stage(), s);
            }
            packRepository.totalsForLots(ids).forEach(p -> packs.put(p.lotId(), p));
        }
        Map<Long, LotFlow> result = new HashMap<>();
        for (ProductionLot l : lots) {
            result.put(l.getId(), LotFlows.of(l.getId(), byLot.getOrDefault(l.getId(), Map.of()),
                    packs.getOrDefault(l.getId(), PackTotals.empty(l.getId())), sqmPerTile(l.getDesign().getTileSize())));
        }
        return result;
    }

    /** Lot -> the press entries of its shifts -> powder used per spray run -> each run's body batches. */
    private List<LotResponse.TraceRun> trace(List<PressEntry> behind) {
        if (behind.isEmpty()) {
            return List.of();
        }
        // Several press entries may draw from the same run: add their tonnes up per run, keeping FIFO order.
        Map<Long, BigDecimal> tonnesByRun = new LinkedHashMap<>();
        Map<Long, SprayRun> runs = new LinkedHashMap<>();
        for (PressEntryPowder p : powderRepository.findForEntries(behind.stream().map(PressEntry::getId).toList())) {
            runs.putIfAbsent(p.getSprayRun().getId(), p.getSprayRun());
            tonnesByRun.merge(p.getSprayRun().getId(), p.getTonnes(), BigDecimal::add);
        }
        if (runs.isEmpty()) {
            return List.of();
        }
        Map<Long, List<LotResponse.BatchRef>> batchesByRun = new LinkedHashMap<>();
        for (SprayRunBatch rb : runRepository.findBatchesOfRuns(runs.keySet())) {
            batchesByRun.computeIfAbsent(rb.getRun().getId(), k -> new ArrayList<>())
                    .add(new LotResponse.BatchRef(rb.getBodyBatch().getId(), rb.getBodyBatch().getBatchNo()));
        }
        return runs.values().stream()
                .map(r -> new LotResponse.TraceRun(r.getId(), r.getRunNo(), r.getSilo().getCode(), r.getProdDate(),
                        tonnesByRun.get(r.getId()), batchesByRun.getOrDefault(r.getId(), List.of())))
                .toList();
    }

    /**
     * A machine with a design running on it can't be swapped out under the lot: the supervisor closes the lot
     * first (or starts the next design on the new machine). Throwing here stops the replacement before it starts.
     */
    @EventListener
    public void onMachineReplacing(MachineReplacingEvent e) {
        lotRepository.findFirstByPrinterIdAndStatus(e.oldMachineId(), LotStatus.OPEN).ifPresent(lot -> {
            throw new BusinessException(e.oldCode() + " is running design lot " + lot.getLotNo()
                    + ". Close the lot before replacing the machine.");
        });
    }
}
