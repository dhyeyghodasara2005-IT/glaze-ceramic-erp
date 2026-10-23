package com.ceramicerp.production.dto;

import com.ceramicerp.production.CheckVerdict;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The finishing home screen (cooling -> polishing -> sorting & packing): every lot with tiles
 * still on their way to the box, and today's figures.
 *
 * @param topGradeName the best sellable grade (e.g. Premium) - "top %" below is the share in it
 */
public record FinishingOverview(
        LocalDate prodDate,
        Long shiftId,
        String shiftCode,
        String shiftName,
        String topGradeName,
        List<LotLine> lots,
        Day today
) {
    /** @param topPct share of the lot's sorted tiles packed in the top grade so far (null = nothing sorted) */
    public record LotLine(LotSummary lot, LotFlow flow, CheckRef lastCheck, BigDecimal topPct) {
    }

    /** The latest quality check of a lot: what the cooling area thought of it. */
    public record CheckRef(Long id, String checkNo, LocalDate checkDate, CheckVerdict verdict, BigDecimal expectedTopPct) {
    }

    /**
     * @param sorted  tiles that went into sorting today (packed + broken)
     * @param topPct  share of today's sorted tiles in the top grade
     * @param grades  boxes per grade today, best grade first
     */
    public record Day(long polishIn, long polishRejects, BigDecimal powerKwh, long sorted, long broken, long boxes,
                      BigDecimal packedSqm, BigDecimal topPct, long checks, List<GradeCount> grades) {
    }
}
