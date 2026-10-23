package com.ceramicerp.production;

import com.ceramicerp.production.dto.LotFlow;
import com.ceramicerp.production.dto.LotTotals;
import com.ceramicerp.production.dto.PackTotals;
import com.ceramicerp.production.dto.StageTotals;

import java.math.BigDecimal;
import java.util.Map;

/** Builds a lot's funnel from the press, line-stage and packing totals. Pure arithmetic, no database. */
final class LotFlows {

    private LotFlows() {
    }

    static LotFlow of(LotTotals press, Map<LineStage, StageTotals> stages, PackTotals pack, BigDecimal sqmPerTile) {
        StageTotals glaze = stage(stages, press.lotId(), LineStage.GLAZE);
        StageTotals kiln = stage(stages, press.lotId(), LineStage.KILN);
        StageTotals polish = stage(stages, press.lotId(), LineStage.POLISH);
        long pressGood = press.good();
        return new LotFlow(press.pressed(), press.pressRejects() + press.dryerRejects(), pressGood,
                glaze.tilesIn(), glaze.rejects(), glaze.tilesOut(),
                kiln.tilesIn(), kiln.rejects(), kiln.tilesOut(),
                polish.tilesIn(), polish.rejects(), polish.tilesOut(),
                pack.sorted(), pack.broken(), pack.packed(), pack.boxes(),
                // each stage takes what the one before it put out
                pressGood - glaze.tilesIn(), glaze.tilesOut() - kiln.tilesIn(),
                kiln.tilesOut() - polish.tilesIn(), polish.tilesOut() - pack.sorted(),
                ProductionRules.sqm(kiln.tilesOut(), sqmPerTile), ProductionRules.sqm(pack.packed(), sqmPerTile),
                ProductionRules.pct(kiln.tilesOut(), press.pressed()), ProductionRules.pct(pack.packed(), press.pressed()));
    }

    private static StageTotals stage(Map<LineStage, StageTotals> stages, Long lotId, LineStage stage) {
        return stages.getOrDefault(stage, StageTotals.empty(lotId, stage));
    }
}
