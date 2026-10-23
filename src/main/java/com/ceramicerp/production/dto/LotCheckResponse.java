package com.ceramicerp.production.dto;

import com.ceramicerp.production.CheckVerdict;
import com.ceramicerp.production.LotCheck;
import com.ceramicerp.store.DocStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** One quality check - small enough to serve lists and the detail view alike. */
public record LotCheckResponse(
        Long id,
        Long plantId,
        String plantCode,
        String checkNo,
        Long lotId,
        String lotNo,
        String designName,
        String pressCode,
        LocalDate checkDate,
        Long shiftId,
        String shiftCode,
        DocStatus status,
        int samples,
        BigDecimal centreBendMm,
        BigDecimal edgeBendMm,
        BigDecimal warpageMm,
        BigDecimal lengthMm,
        BigDecimal widthMm,
        BigDecimal thicknessMm,
        CheckVerdict verdict,
        BigDecimal expectedTopPct,
        String remarks,
        Instant cancelledAt,
        String cancelledBy,
        String cancelReason,
        Instant createdAt,
        String createdBy,
        Long version
) {
    /** Needs plant, lot, lot.design, lot.press and shift loaded. */
    public static LotCheckResponse from(LotCheck c) {
        var lot = c.getLot();
        return new LotCheckResponse(c.getId(), c.getPlant().getId(), c.getPlant().getCode(), c.getCheckNo(), lot.getId(),
                lot.getLotNo(), lot.getDesign().getName(), lot.getPress().getCode(), c.getCheckDate(),
                c.getShift().getId(), c.getShift().getCode(), c.getStatus(), c.getSamples(), c.getCentreBendMm(),
                c.getEdgeBendMm(), c.getWarpageMm(), c.getLengthMm(), c.getWidthMm(), c.getThicknessMm(),
                c.getVerdict(), c.getExpectedTopPct(), c.getRemarks(), c.getCancelledAt(), c.getCancelledBy(),
                c.getCancelReason(), c.getCreatedAt(), c.getCreatedBy(), c.getVersion());
    }
}
