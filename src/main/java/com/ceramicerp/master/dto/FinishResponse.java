package com.ceramicerp.master.dto;

import com.ceramicerp.master.Finish;

/** What the API sends back for a finish. */
public record FinishResponse(
        Long id,
        String code,
        String name,
        boolean active,
        Long version
) {
    public static FinishResponse from(Finish f) {
        return new FinishResponse(f.getId(), f.getCode(), f.getName(), f.isActive(), f.getVersion());
    }
}