package com.ceramicerp.production.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** The press & dryer supervisor's home screen in one call. */
public record PressOverview(
        LocalDate prodDate,
        Long shiftId,
        String shiftCode,
        String shiftName,
        List<Line> lines,
        Day today,
        List<SiloStatus> silos
) {
    /** One press line and the lot it is running (null when idle). */
    public record Line(Long pressId, String pressCode, String pressName, LotSummary lot) {
    }

    public record Day(long entries, long pressed, long rejects, long good, BigDecimal goodSqm) {
    }
}
