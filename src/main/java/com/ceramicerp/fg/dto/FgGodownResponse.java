package com.ceramicerp.fg.dto;

import com.ceramicerp.fg.FgGodown;

/** A finished-goods godown with how many pillars it has and how many boxes stand in it. */
public record FgGodownResponse(Long id, Long plantId, String code, String name, boolean active, long pillars,
                               long boxes, Long version) {

    public static FgGodownResponse from(FgGodown g, long pillars, long boxes) {
        return new FgGodownResponse(g.getId(), g.getPlant().getId(), g.getCode(), g.getName(), g.isActive(), pillars,
                boxes, g.getVersion());
    }
}
