package com.ceramicerp.party;

/**
 * The three kinds of party. Each has its own screen and code prefix (DLR-0001, SUP-0001, TRN-0001).
 */
public enum PartyType {
    /** Customers who buy tiles: dealers, distributors, builders, exporters. */
    DEALER("DLR-"),
    /** Who you buy from: clay, frit, ink, packing, gas, spares. */
    SUPPLIER("SUP-"),
    /** Truck / container transporters used for dispatch. */
    TRANSPORTER("TRN-");

    private final String codePrefix;

    PartyType(String codePrefix) {
        this.codePrefix = codePrefix;
    }

    public String getCodePrefix() {
        return codePrefix;
    }
}
