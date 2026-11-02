package com.ceramicerp.costing.dto;

import com.ceramicerp.costing.CostRate;
import com.ceramicerp.energy.MeterKind;
import com.ceramicerp.energy.MeterUnit;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CostRateResponse(Long id, MeterKind kind, MeterUnit unit, LocalDate validFrom, BigDecimal rate, String note,
                               String by) {

    public static CostRateResponse from(CostRate r) {
        return new CostRateResponse(r.getId(), r.getKind(), r.getUnit(), r.getValidFrom(), r.getRate(), r.getNote(), r.getUpdatedBy());
    }
}
