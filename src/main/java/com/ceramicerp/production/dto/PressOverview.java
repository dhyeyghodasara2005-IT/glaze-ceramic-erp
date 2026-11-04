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
    /** One press and what it pressed today (posted entries). Green tiles: no design here. */
    public record Line(Long pressId, String pressCode, String pressName, long entries, long pressed, long good) {
    }

    public record Day(long entries, long pressed, long rejects, long good) {
    }
}
