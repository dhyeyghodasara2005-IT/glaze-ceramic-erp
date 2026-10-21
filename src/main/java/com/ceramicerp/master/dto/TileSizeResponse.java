package com.ceramicerp.master.dto;

import com.ceramicerp.master.TileSize;

import java.math.BigDecimal;

/** What the API sends back for a size, including the calculated sqmPerBox. */
public record TileSizeResponse(
        Long id,
        String code,
        String name,
        Integer lengthMm,
        Integer widthMm,
        BigDecimal thicknessMm,
        Integer pcsPerBox,
        BigDecimal sqmPerBox,
        BigDecimal boxWeightKg,
        Integer boxesPerPallet,
        boolean active,
        Long version
) {
    public static TileSizeResponse from(TileSize s) {
        return new TileSizeResponse(s.getId(), s.getCode(), s.getName(), s.getLengthMm(), s.getWidthMm(),
                s.getThicknessMm(), s.getPcsPerBox(), s.getSqmPerBox(), s.getBoxWeightKg(),
                s.getBoxesPerPallet(), s.isActive(), s.getVersion());
    }
}