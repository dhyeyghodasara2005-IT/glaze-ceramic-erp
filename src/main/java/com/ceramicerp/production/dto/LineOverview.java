package com.ceramicerp.production.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The glaze & kiln home screen: every lot that is running (or closed in the last days, its tiles
 * may still be in the line) with its funnel, and today's figures.
 */
public record LineOverview(
        LocalDate prodDate,
        Long shiftId,
        String shiftCode,
        String shiftName,
        List<LotLine> lots,
        Day today
) {
    public record LotLine(LotSummary lot, LotFlow flow) {
    }

    /** @param gasPerSqm kiln gas / square metres fired today */
    public record Day(long glazeIn, long glazeRejects, long kilnIn, long kilnRejects, BigDecimal firedSqm,
                      BigDecimal gasScm, BigDecimal gasPerSqm) {
    }
}
