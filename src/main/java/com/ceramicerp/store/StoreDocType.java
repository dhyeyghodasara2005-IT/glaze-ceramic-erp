package com.ceramicerp.store;

/** The three kinds of store document. The prefix goes into the document number: UNIT-1/GRN/26-27/0001. */
public enum StoreDocType {
    /** Goods receipt note: material received from a supplier - stock goes UP. */
    GRN("GRN"),
    /** Material issued to production (a machine / department) - stock goes DOWN. */
    ISSUE("ISS"),
    /** Opening stock, physical count corrections, damage - stock goes up or down. */
    ADJUSTMENT("ADJ");

    private final String prefix;

    StoreDocType(String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }
}
