package com.ceramicerp.sales;

import com.ceramicerp.party.PartyType;

/** Who the bill for a loading slip goes to. */
public enum SalesOrderType {
    /** A normal sale: the dealer named on the slip. */
    SALE(PartyType.DEALER),
    /** Jobwork: the slip hides the party; the bill goes to the company whose jobwork it is. */
    JOBWORK(PartyType.JOBWORK);

    private final PartyType partyType;

    SalesOrderType(PartyType partyType) {
        this.partyType = partyType;
    }

    /** The kind of party this order is billed to. */
    public PartyType partyType() {
        return partyType;
    }
}
