package com.ceramicerp.master.dto;

import com.ceramicerp.master.MaterialGroup;

public record MaterialGroupResponse(Long id, String code, String name, boolean active, Long version) {

    public static MaterialGroupResponse from(MaterialGroup g) {
        return new MaterialGroupResponse(g.getId(), g.getCode(), g.getName(), g.isActive(), g.getVersion());
    }
}
