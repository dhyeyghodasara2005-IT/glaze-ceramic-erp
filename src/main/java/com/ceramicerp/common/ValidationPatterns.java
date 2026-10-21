package com.ceramicerp.common;

/** Regex patterns for Indian business identifiers, shared by request DTOs. */
public final class ValidationPatterns {

    private ValidationPatterns() {
    }

    /** Short codes like "MORBI-1", "ACME". Stored upper-case. */
    public static final String CODE = "^[A-Za-z0-9_-]{2,20}$";

    /** 15-char GSTIN, e.g. 24ABCDE1234F1Z5 (24 = Gujarat). Case-insensitive input. */
    public static final String GSTIN = "^[0-9]{2}[A-Za-z]{5}[0-9]{4}[A-Za-z][1-9A-Za-z][Zz][0-9A-Za-z]$";

    /** 10-char PAN, e.g. ABCDE1234F. */
    public static final String PAN = "^[A-Za-z]{5}[0-9]{4}[A-Za-z]$";

    /** 6-digit Indian PIN code (Morbi = 363641). */
    public static final String PINCODE = "^[1-9][0-9]{5}$";

    public static final String PHONE = "^[0-9+\\- ]{7,20}$";
}
