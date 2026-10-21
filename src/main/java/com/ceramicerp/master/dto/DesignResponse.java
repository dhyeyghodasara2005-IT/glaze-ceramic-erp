package com.ceramicerp.master.dto;

import com.ceramicerp.master.Design;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What the API sends back for a design. Includes the names of its category, size and finish
 * so the screen can show "GVT · 600X1200 · Glossy" without extra calls.
 */
public record DesignResponse(
        Long id,
        String code,
        String name,
        Long categoryId,
        String categoryCode,
        String categoryName,
        Long sizeId,
        String sizeCode,
        String sizeName,
        BigDecimal sqmPerBox,
        Long finishId,
        String finishCode,
        String finishName,
        int faces,
        LocalDate launchDate,
        String description,
        boolean active,
        Long version
) {
    /** Must be called inside a transaction (reads the lazy category/size/finish). */
    public static DesignResponse from(Design d) {
        var c = d.getCategory();
        var s = d.getTileSize();
        var f = d.getFinish();
        return new DesignResponse(d.getId(), d.getCode(), d.getName(),
                c.getId(), c.getCode(), c.getName(),
                s.getId(), s.getCode(), s.getName(), s.getSqmPerBox(),
                f.getId(), f.getCode(), f.getName(),
                d.getFaces(), d.getLaunchDate(), d.getDescription(), d.isActive(), d.getVersion());
    }
}
