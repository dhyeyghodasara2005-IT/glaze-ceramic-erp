package com.ceramicerp.master.dto;

import com.ceramicerp.master.UnitOfMeasure;

public record UnitOfMeasureResponse(Long id, String code, String name, int decimals, boolean active, Long version) {

    public static UnitOfMeasureResponse from(UnitOfMeasure u) {
        return new UnitOfMeasureResponse(u.getId(), u.getCode(), u.getName(), u.getDecimals(), u.isActive(), u.getVersion());
    }
}
