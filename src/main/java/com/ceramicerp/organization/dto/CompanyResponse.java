package com.ceramicerp.organization.dto;

import com.ceramicerp.organization.Company;

/** What the API returns for a company. Entities are never returned directly. */
public record CompanyResponse(
        Long id,
        String code,
        String name,
        String legalName,
        String gstin,
        String pan,
        String addressLine,
        String city,
        String state,
        String pincode,
        String phone,
        String email,
        boolean active,
        Long version
) {
    public static CompanyResponse from(Company c) {
        return new CompanyResponse(c.getId(), c.getCode(), c.getName(), c.getLegalName(), c.getGstin(),
                c.getPan(), c.getAddressLine(), c.getCity(), c.getState(), c.getPincode(), c.getPhone(),
                c.getEmail(), c.isActive(), c.getVersion());
    }
}
