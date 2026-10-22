package com.ceramicerp.production.dto;

import com.ceramicerp.production.SprayRun;
import com.ceramicerp.store.DocStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SprayRunSummary(
        Long id,
        String runNo,
        LocalDate prodDate,
        String shiftCode,
        String sprayDryerCode,
        String siloCode,
        String siloName,
        BigDecimal powderTon,
        BigDecimal powderMoisture,
        DocStatus status
) {
    public static SprayRunSummary from(SprayRun r) {
        return new SprayRunSummary(r.getId(), r.getRunNo(), r.getProdDate(), r.getShift().getCode(),
                r.getSprayDryer().getCode(), r.getSilo().getCode(), r.getSilo().getName(), r.getPowderTon(),
                r.getPowderMoisture(), r.getStatus());
    }
}
