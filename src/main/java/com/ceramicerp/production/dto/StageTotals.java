package com.ceramicerp.production.dto;

import com.ceramicerp.production.LineStage;

import java.math.BigDecimal;

/** Posted glaze or kiln entries of one lot, added up by the database. */
public record StageTotals(Long lotId, LineStage stage, long entries, long tilesIn, long rejects, BigDecimal gasScm) {

    public StageTotals {
        gasScm = gasScm == null ? BigDecimal.ZERO : gasScm;
    }

    public static StageTotals empty(Long lotId, LineStage stage) {
        return new StageTotals(lotId, stage, 0, 0, 0, BigDecimal.ZERO);
    }

    public long tilesOut() {
        return tilesIn - rejects;
    }
}
