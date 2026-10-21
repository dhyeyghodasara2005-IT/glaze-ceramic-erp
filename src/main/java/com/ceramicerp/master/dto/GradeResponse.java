package com.ceramicerp.master.dto;

import com.ceramicerp.master.Grade;

/** What the API sends back for a grade. */
public record GradeResponse(
        Long id,
        String code,
        String name,
        int sortOrder,
        boolean sellable,
        boolean active,
        Long version
) {
    public static GradeResponse from(Grade g) {
        return new GradeResponse(g.getId(), g.getCode(), g.getName(), g.getSortOrder(), g.isSellable(),
                g.isActive(), g.getVersion());
    }
}
