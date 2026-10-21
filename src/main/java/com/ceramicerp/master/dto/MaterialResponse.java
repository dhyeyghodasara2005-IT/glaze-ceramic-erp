package com.ceramicerp.master.dto;

import com.ceramicerp.master.Material;

import java.math.BigDecimal;

/** A material with its group and unit flattened in, ready for the screen. */
public record MaterialResponse(
        Long id,
        String code,
        String name,
        Long groupId,
        String groupCode,
        String groupName,
        Long unitId,
        String unitCode,
        int unitDecimals,
        String hsnCode,
        BigDecimal gstRate,
        BigDecimal reorderLevel,
        String description,
        boolean active,
        Long version
) {
    /** Must be called inside a transaction (reads the lazy group/unit). */
    public static MaterialResponse from(Material m) {
        var g = m.getGroup();
        var u = m.getUnit();
        return new MaterialResponse(m.getId(), m.getCode(), m.getName(),
                g.getId(), g.getCode(), g.getName(),
                u.getId(), u.getCode(), u.getDecimals(),
                m.getHsnCode(), m.getGstRate(), m.getReorderLevel(), m.getDescription(), m.isActive(), m.getVersion());
    }
}
