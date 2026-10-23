package com.ceramicerp.production.dto;

/** Posted pack entries of one lot, added up by the database. */
public record PackTotals(Long lotId, long entries, long sorted, long broken, long packed, long boxes) {

    public static PackTotals empty(Long lotId) {
        return new PackTotals(lotId, 0, 0, 0, 0, 0);
    }
}
