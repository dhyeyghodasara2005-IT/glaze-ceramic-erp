package com.ceramicerp.sales.dto;

import com.ceramicerp.party.Party;

import java.math.BigDecimal;

/**
 * Who the order is billed to, with what the office checks before confirming.
 * What the dealer owes today comes with the dealer ledger (step 6d).
 */
public record SalesParty(Long id, String code, String name, String city, String state, String gstin,
                         BigDecimal creditLimit, Integer creditDays, boolean active) {

    public static SalesParty from(Party p) {
        return new SalesParty(p.getId(), p.getCode(), p.getName(), p.getCity(), p.getState(), p.getGstin(),
                p.getCreditLimit(), p.getCreditDays(), p.isActive());
    }
}
