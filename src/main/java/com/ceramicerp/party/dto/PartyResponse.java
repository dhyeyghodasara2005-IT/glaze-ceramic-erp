package com.ceramicerp.party.dto;

import com.ceramicerp.party.GstRegistration;
import com.ceramicerp.party.Party;
import com.ceramicerp.party.PartyType;

import java.math.BigDecimal;

public record PartyResponse(
        Long id,
        PartyType partyType,
        String code,
        String name,
        String contactPerson,
        GstRegistration gstRegistration,
        String gstin,
        String pan,
        String addressLine,
        String city,
        String state,
        String stateCode,
        String pincode,
        String country,
        String phone,
        String mobile,
        String email,
        String region,
        BigDecimal creditLimit,
        Integer creditDays,
        String bankName,
        String bankAccountNo,
        String bankIfsc,
        String notes,
        boolean active,
        Long version
) {
    public static PartyResponse from(Party p) {
        return new PartyResponse(p.getId(), p.getPartyType(), p.getCode(), p.getName(), p.getContactPerson(),
                p.getGstRegistration(), p.getGstin(), p.getPan(), p.getAddressLine(), p.getCity(), p.getState(),
                p.getStateCode(), p.getPincode(), p.getCountry(), p.getPhone(), p.getMobile(), p.getEmail(),
                p.getRegion(), p.getCreditLimit(), p.getCreditDays(), p.getBankName(), p.getBankAccountNo(),
                p.getBankIfsc(), p.getNotes(), p.isActive(), p.getVersion());
    }
}
