package com.ceramicerp.sales.dto;

import com.ceramicerp.master.Design;

import java.math.BigDecimal;

/** A design for the order editor, with what it needs to total m² and weight while the slip is typed. */
public record SalesDesign(Long id, String code, String name, String sizeCode, BigDecimal sqmPerBox,
                          BigDecimal boxWeightKg) {

    /** Needs the size loaded. */
    public static SalesDesign from(Design d) {
        var size = d.getTileSize();
        return new SalesDesign(d.getId(), d.getCode(), d.getName(), size.getCode(), size.getSqmPerBox(),
                size.getBoxWeightKg());
    }
}
