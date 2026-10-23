package com.ceramicerp.production;

import com.ceramicerp.master.Grade;
import com.ceramicerp.master.GradeRepository;
import com.ceramicerp.production.dto.CurrentShiftResponse;
import com.ceramicerp.production.dto.FinishingOverview;
import com.ceramicerp.production.dto.GradeCount;
import com.ceramicerp.production.dto.LotCount;
import com.ceramicerp.production.dto.LotFlow;
import com.ceramicerp.security.PlantAccess;
import com.ceramicerp.store.DocStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.ceramicerp.production.ProductionRules.pct;
import static com.ceramicerp.production.ProductionRules.sqm;
import static com.ceramicerp.production.ProductionRules.sqmPerTile;

/** The finishing home screen: cooling area -> polishing -> sorting & packing. */
@Service
@RequiredArgsConstructor
public class FinishingService {

    /** Fired tiles can sit in the cooling area for a while: look this far back for lots still finishing. */
    private static final Duration FINISH_WINDOW = Duration.ofDays(45);

    private final ProductionLotRepository lotRepository;
    private final LineEntryRepository lineRepository;
    private final PackEntryRepository packRepository;
    private final LotCheckRepository checkRepository;
    private final GradeRepository gradeRepository;
    private final LotService lotService;
    private final BodyPrepService bodyPrepService;

    @Transactional(readOnly = true)
    public FinishingOverview overview(Long plantId) {
        PlantAccess.require(plantId);
        CurrentShiftResponse now = bodyPrepService.currentShift();
        Optional<Grade> top = ProductionRules.topGrade(gradeRepository);

        List<ProductionLot> candidates = lotRepository.findSelectable(plantId, Instant.now().minus(FINISH_WINDOW));
        Map<Long, LotFlow> flows = lotService.flows(candidates, lotService.pressTotals(candidates));
        List<ProductionLot> lots = candidates.stream().filter(l -> finishing(l, flows.get(l.getId()))).toList();
        List<Long> ids = lots.stream().map(ProductionLot::getId).toList();

        // newest first, so the first check seen per lot is its latest
        Map<Long, LotCheck> lastCheck = new HashMap<>();
        if (!ids.isEmpty()) {
            checkRepository.findPostedForLots(ids).forEach(c -> lastCheck.putIfAbsent(c.getLot().getId(), c));
        }
        Map<Long, Long> topPieces = top.isEmpty() || ids.isEmpty() ? Map.of()
                : packRepository.piecesOfGrade(ids, top.get().getId()).stream()
                        .collect(Collectors.toMap(LotCount::lotId, LotCount::count));

        List<FinishingOverview.LotLine> lines = lotService.summaries(lots).stream().map(s -> {
            LotFlow flow = flows.get(s.id());
            return new FinishingOverview.LotLine(s, flow, checkRef(lastCheck.get(s.id())),
                    top.isEmpty() ? null : pct(topPieces.getOrDefault(s.id(), 0L), flow.sorted()));
        }).toList();

        return new FinishingOverview(now.prodDate(), now.shiftId(), now.code(), now.name(),
                top.map(Grade::getName).orElse(null), lines, today(plantId, now, top));
    }

    /** A lot is in finishing while fired tiles are still on their way to the box (a running lot stays while it fires). */
    private static boolean finishing(ProductionLot lot, LotFlow flow) {
        return flow.kilnOut() > 0
                && (lot.getStatus() == LotStatus.OPEN || flow.waitingPolish() != 0 || flow.waitingSort() != 0);
    }

    private static FinishingOverview.CheckRef checkRef(LotCheck c) {
        return c == null ? null
                : new FinishingOverview.CheckRef(c.getId(), c.getCheckNo(), c.getCheckDate(), c.getVerdict(), c.getExpectedTopPct());
    }

    private FinishingOverview.Day today(Long plantId, CurrentShiftResponse now, Optional<Grade> top) {
        long polishIn = 0;
        long polishRejects = 0;
        BigDecimal power = BigDecimal.ZERO;
        for (LineEntry e : lineRepository.findByPlantIdAndProdDateAndStatus(plantId, now.prodDate(), DocStatus.POSTED)) {
            if (e.getStage() == LineStage.POLISH) {
                polishIn += e.getTilesIn();
                polishRejects += e.getRejects();
                power = e.getPowerKwh() == null ? power : power.add(e.getPowerKwh());
            }
        }

        long sorted = 0;
        long broken = 0;
        long boxes = 0;
        BigDecimal packedSqm = BigDecimal.ZERO;
        for (PackEntry e : packRepository.findByPlantIdAndProdDateAndStatus(plantId, now.prodDate(), DocStatus.POSTED)) {
            sorted += e.getTilesSorted();
            broken += e.getBroken();
            boxes += e.getBoxes();
            BigDecimal area = sqm(e.getPiecesPacked(), sqmPerTile(e.getLot().getDesign().getTileSize()));
            packedSqm = area == null ? packedSqm : packedSqm.add(area);
        }

        List<GradeCount> grades = packRepository.gradesOfDay(plantId, now.prodDate());
        long topPieces = top.map(g -> grades.stream().filter(c -> c.gradeId().equals(g.getId()))
                .mapToLong(GradeCount::pieces).sum()).orElse(0L);
        BigDecimal topPct = top.isEmpty() ? null : pct(topPieces, sorted);
        long checks = checkRepository.countByPlantIdAndCheckDateAndStatus(plantId, now.prodDate(), DocStatus.POSTED);
        return new FinishingOverview.Day(polishIn, polishRejects, power, sorted, broken, boxes, packedSqm, topPct,
                checks, grades);
    }
}
