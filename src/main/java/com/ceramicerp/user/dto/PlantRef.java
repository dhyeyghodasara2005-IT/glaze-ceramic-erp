package com.ceramicerp.user.dto;

import com.ceramicerp.organization.Plant;

/** Short form of a plant, used inside user responses. */
public record PlantRef(Long id, String code, String name) {
    public static PlantRef from(Plant p) {
        return new PlantRef(p.getId(), p.getCode(), p.getName());
    }
}
