package com.ceramicerp.lab.dto;

import com.ceramicerp.lab.LabParameter;

import java.math.BigDecimal;

public record LabParameterResponse(Long id, String code, String name, String unit, BigDecimal minValue,
                                   BigDecimal maxValue, int sortOrder, boolean active) {

    public static LabParameterResponse from(LabParameter p) {
        return new LabParameterResponse(p.getId(), p.getCode(), p.getName(), p.getUnit(), p.getMinValue(),
                p.getMaxValue(), p.getSortOrder(), p.isActive());
    }
}
