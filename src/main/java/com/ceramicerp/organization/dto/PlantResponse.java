package com.ceramicerp.organization.dto;

import com.ceramicerp.organization.Plant;

import java.math.BigDecimal;

public record PlantResponse(
        Long id,
        Long companyId,
        String companyCode,
        String companyName,
        String code,
        String name,
        String gstin,
        String addressLine,
        String city,
        String state,
        String pincode,
        BigDecimal capacitySqmPerDay,
        boolean active,
        Long version
) {
    public static PlantResponse from(Plant p) {
        return new PlantResponse(p.getId(), p.getCompany().getId(), p.getCompany().getCode(),
                p.getCompany().getName(), p.getCode(), p.getName(), p.getGstin(), p.getAddressLine(),
                p.getCity(), p.getState(), p.getPincode(), p.getCapacitySqmPerDay(), p.isActive(),
                p.getVersion());
    }
}
