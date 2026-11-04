package com.ceramicerp.production;

import com.ceramicerp.production.dto.LotFlow;
import com.ceramicerp.production.dto.PackTotals;
import com.ceramicerp.production.dto.StageTotals;

import java.math.BigDecimal;
import java.util.Map;

/** Builds a lot's funnel from its line-stage and packing totals. Pure arithmetic, no database. */
final class LotFlows {

    private LotFlows() {
    }

    static LotFlow of(Long lotId, Map<LineStage, StageTotals> stages, PackTotals pack, BigDecimal sqmPerTile) {
        StageTotals glaze = stage(stages, lotId, LineStage.GLAZE);
        StageTotals kiln = stage(stages, lotId, LineStage.KILN);
        StageTotals polish = stage(stages, lotId, LineStage.POLISH);
        return new LotFlow(glaze.tilesIn(), glaze.rejects(), glaze.tilesOut(),
                kiln.tilesIn(), kiln.rejects(), kiln.tilesOut(),
                polish.tilesIn(), polish.rejects(), polish.tilesOut(),
                pack.sorted(), pack.broken(), pack.packed(), pack.boxes(),
                // each stage takes what the one before it put out
                glaze.tilesOut() - kiln.tilesIn(), kiln.tilesOut() - polish.tilesIn(), polish.tilesOut() - pack.sorted(),
                ProductionRules.sqm(kiln.tilesOut(), sqmPerTile), ProductionRules.sqm(pack.packed(), sqmPerTile),
                ProductionRules.pct(kiln.tilesOut(), glaze.tilesIn()), ProductionRules.pct(pack.packed(), glaze.tilesIn()));
    }

    private static StageTotals stage(Map<LineStage, StageTotals> stages, Long lotId, LineStage stage) {
        return stages.getOrDefault(stage, StageTotals.empty(lotId, stage));
    }
}
