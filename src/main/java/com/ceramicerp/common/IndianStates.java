package com.ceramicerp.common;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GST state codes - the first 2 digits of every GSTIN (24 = Gujarat).
 * Invoices use them to decide IGST (different state) vs CGST + SGST (same state).
 * The frontend has the same list in api/indianStates.ts.
 */
public final class IndianStates {

    private IndianStates() {
    }

    private static final Map<String, String> STATES;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("01", "Jammu and Kashmir");
        m.put("02", "Himachal Pradesh");
        m.put("03", "Punjab");
        m.put("04", "Chandigarh");
        m.put("05", "Uttarakhand");
        m.put("06", "Haryana");
        m.put("07", "Delhi");
        m.put("08", "Rajasthan");
        m.put("09", "Uttar Pradesh");
        m.put("10", "Bihar");
        m.put("11", "Sikkim");
        m.put("12", "Arunachal Pradesh");
        m.put("13", "Nagaland");
        m.put("14", "Manipur");
        m.put("15", "Mizoram");
        m.put("16", "Tripura");
        m.put("17", "Meghalaya");
        m.put("18", "Assam");
        m.put("19", "West Bengal");
        m.put("20", "Jharkhand");
        m.put("21", "Odisha");
        m.put("22", "Chhattisgarh");
        m.put("23", "Madhya Pradesh");
        m.put("24", "Gujarat");
        m.put("26", "Dadra and Nagar Haveli and Daman and Diu");
        m.put("27", "Maharashtra");
        m.put("29", "Karnataka");
        m.put("30", "Goa");
        m.put("31", "Lakshadweep");
        m.put("32", "Kerala");
        m.put("33", "Tamil Nadu");
        m.put("34", "Puducherry");
        m.put("35", "Andaman and Nicobar Islands");
        m.put("36", "Telangana");
        m.put("37", "Andhra Pradesh");
        m.put("38", "Ladakh");
        m.put("97", "Other Territory");
        STATES = Collections.unmodifiableMap(m);
    }

    /** "24" -> "Gujarat", unknown code -> null. */
    public static String name(String code) {
        return code == null ? null : STATES.get(code);
    }

    public static boolean isValid(String code) {
        return code != null && STATES.containsKey(code);
    }
}
