package com.ceramicerp.production.dto;

import com.ceramicerp.production.LotStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** One design lot in a list, with its press totals so far. */
public record LotSummary(
        Long id,
        String lotNo,
        Long designId,
        String designCode,
        String designName,
        String sizeCode,
        String finishName,
        BigDecimal sqmPerTile,
        Long pressId,
        String pressCode,
        LotStatus status,
        LocalDate openedDate,
        String openedShiftCode,
        Instant openedAt,
        Instant closedAt,
        long pressed,
        long good,
        BigDecimal goodSqm
) {
}
