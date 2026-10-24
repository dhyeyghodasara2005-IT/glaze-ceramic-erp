package com.ceramicerp.party;

/**
 * The kinds of party. Each has its own screen and code prefix (DLR-0001, SUP-0001, TRN-0001, JWP-0001).
 */
public enum PartyType {
    /** Customers who buy tiles: dealers, distributors, builders, exporters. */
    DEALER("DLR-", "dealer"),
    /** Who you buy from: clay, frit, ink, packing, gas, spares. */
    SUPPLIER("SUP-", "supplier"),
    /** Truck / container transporters used for dispatch. */
    TRANSPORTER("TRN-", "transporter"),
    /** Companies whose tiles you make on jobwork: the bill for a jobwork loading slip goes to them. */
    JOBWORK("JWP-", "jobwork principal");

    private final String codePrefix;
    private final String label;

    PartyType(String codePrefix, String label) {
        this.codePrefix = codePrefix;
        this.label = label;
    }

    /** How messages name this kind: "Another jobwork principal already has GSTIN ...". */
    public String label() {
        return label;
    }

    public String getCodePrefix() {
        return codePrefix;
    }

    /** Parties you bill (dealers and jobwork principals): they have a credit limit. */
    public boolean isCustomer() {
        return this == DEALER || this == JOBWORK;
    }
}
