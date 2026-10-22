package com.ceramicerp.master.dto;

import com.ceramicerp.master.BodyRecipe;
import com.ceramicerp.master.BodyRecipeLine;

import java.math.BigDecimal;
import java.util.List;

public record BodyRecipeResponse(Long id, String code, String name, String description, boolean active,
                                 Long version, List<Line> lines) {

    public record Line(Long id, int lineNo, Long materialId, String materialCode, String materialName,
                       String unitCode, int unitDecimals, BigDecimal qtyPerTon) {

        static Line from(BodyRecipeLine l) {
            var m = l.getMaterial();
            return new Line(l.getId(), l.getLineNo(), m.getId(), m.getCode(), m.getName(), m.getUnit().getCode(),
                    m.getUnit().getDecimals(), l.getQtyPerTon());
        }
    }

    public static BodyRecipeResponse from(BodyRecipe r) {
        return new BodyRecipeResponse(r.getId(), r.getCode(), r.getName(), r.getDescription(), r.isActive(),
                r.getVersion(), r.getLines().stream().map(Line::from).toList());
    }
}
