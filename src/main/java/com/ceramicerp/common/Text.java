package com.ceramicerp.common;

import java.util.Locale;

/** Small helpers for cleaning user input before saving. */
public final class Text {

    private Text() {
    }

    /** "  abc " -> "abc", "   " -> null, null -> null */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Trims and upper-cases codes like GSTIN, PAN, plant code. */
    public static String upperOrNull(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }
}
