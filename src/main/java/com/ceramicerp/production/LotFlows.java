package com.ceramicerp.production;

import com.ceramicerp.production.dto.LotFlow;
import com.ceramicerp.production.dto.LotTotals;
import com.ceramicerp.production.dto.StageTotals;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/** Builds a lot's funnel from the press totals and the glaze / kiln totals. Pure arithmetic, no database. */
final class LotFlows {

    private LotFlows() {
    }

    static LotFlow of(LotTotals press, Map<LineStage, StageTotals> stages, BigDecimal sqmPerTile) {
        StageTotals glaze = stages.getOrDefault(LineStage.GLAZE, StageTotals.empty(press.lotId(), LineStage.GLAZE));
        StageTotals kiln = stages.getOrDefault(LineStage.KILN, StageTotals.empty(press.lotId(), LineStage.KILN));
        long pressGood = press.good();
        BigDecimal yieldPct = press.pressed() == 0 ? null
                : BigDecimal.valueOf(kiln.tilesOut() * 100).divide(BigDecimal.valueOf(press.pressed()), 2, RoundingMode.HALF_UP);
        return new LotFlow(press.pressed(), press.pressRejects() + press.dryerRejects(), pressGood,
                glaze.tilesIn(), glaze.rejects(), glaze.tilesOut(),
                kiln.tilesIn(), kiln.rejects(), kiln.tilesOut(),
                pressGood - glaze.tilesIn(), glaze.tilesOut() - kiln.tilesIn(),
                ProductionRules.sqm(kiln.tilesOut(), sqmPerTile), yieldPct);
    }
}
