package com.ceramicerp.fg;

import com.ceramicerp.common.exception.BusinessException;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The codes printed on box stickers. Packing (production) and the warehouse (finished goods) must tidy them
 * the same way, or "c1" and "C1" would become two different piles of stock.
 */
public final class FgCodes {

    /** Shade and caliber: 01, A3, C-2, 9.5 ... */
    private static final Pattern SHORT = Pattern.compile("[A-Z0-9][A-Z0-9./-]{0,9}");
    /** Batch numbers printed on older boxes (opening stock): B-2309, 23/117 ... */
    private static final Pattern BATCH = Pattern.compile("[A-Z0-9][A-Z0-9./-]{0,39}");

    private FgCodes() {
    }

    /** " a3 " -> "A3", or a clear error. */
    public static String shadeOrCaliber(String value, String what) {
        return tidy(value, what, SHORT, 10);
    }

    public static String batch(String value) {
        return tidy(value, "Batch", BATCH, 40);
    }

    private static String tidy(String value, String what, Pattern pattern, int max) {
        String code = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!pattern.matcher(code).matches()) {
            throw new BusinessException(what + " \"" + (value == null ? "" : value.trim())
                    + "\" can only use letters, digits and . / - (up to " + max + ")");
        }
        return code;
    }
}
