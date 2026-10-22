package com.ceramicerp.store.dto;

import com.ceramicerp.store.StoreLocation;

public record StoreLocationResponse(Long id, Long plantId, String plantCode, String code, String name,
                                    boolean active, Long version) {

    public static StoreLocationResponse from(StoreLocation l) {
        return new StoreLocationResponse(l.getId(), l.getPlant().getId(), l.getPlant().getCode(), l.getCode(),
                l.getName(), l.isActive(), l.getVersion());
    }
}
