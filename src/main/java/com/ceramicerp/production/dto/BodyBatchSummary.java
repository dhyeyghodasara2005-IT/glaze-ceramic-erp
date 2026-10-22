package com.ceramicerp.production.dto;

import com.ceramicerp.production.BodyBatch;
import com.ceramicerp.production.BodyBatchStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** One body batch in a list (no lines). */
public record BodyBatchSummary(
        Long id,
        String batchNo,
        LocalDate prodDate,
        String shiftCode,
        String ballMillCode,
        String ballMillName,
        String recipeCode,
        BigDecimal chargeTon,
        BodyBatchStatus status,
        LocalDateTime millingStart,
        LocalDateTime millingEnd,
        String slipTankCode,
        BigDecimal slipDensity,
        BigDecimal slipViscosity,
        BigDecimal slipResidue
) {
    public static BodyBatchSummary from(BodyBatch b) {
        return new BodyBatchSummary(b.getId(), b.getBatchNo(), b.getProdDate(), b.getShift().getCode(),
                b.getBallMill().getCode(), b.getBallMill().getName(),
                b.getRecipe() == null ? null : b.getRecipe().getCode(), b.getChargeTon(), b.getStatus(),
                b.getMillingStart(), b.getMillingEnd(), b.getSlipTank() == null ? null : b.getSlipTank().getCode(),
                b.getSlipDensity(), b.getSlipViscosity(), b.getSlipResidue());
    }
}
