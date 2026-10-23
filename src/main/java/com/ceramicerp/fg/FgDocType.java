package com.ceramicerp.fg;

import com.ceramicerp.numbering.DocumentKind;

/**
 * The warehouse's documents. The constant names are also the number counters' keys, which must be unique
 * across every kind of document (store, production, finished goods) - these are.
 */
public enum FgDocType implements DocumentKind {
    /** Stock already in the godown on go-live day. UNIT-1/OS/26-27/0001 */
    OPENING("OS", FgSource.OPENING),
    /** Boxes damaged in the godown, written off. UNIT-1/BK/26-27/0001 */
    BREAKAGE("BK", FgSource.BREAKAGE),
    /** Boxes moved to another grade, shade, caliber or pillar. UNIT-1/RG/26-27/0001 */
    REGRADE("RG", FgSource.REGRADE);

    private final String prefix;
    private final FgSource source;

    FgDocType(String prefix, FgSource source) {
        this.prefix = prefix;
        this.source = source;
    }

    @Override
    public String getPrefix() {
        return prefix;
    }

    /** What the stock ledger says moved the boxes. */
    public FgSource source() {
        return source;
    }
}
