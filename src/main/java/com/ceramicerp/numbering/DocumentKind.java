package com.ceramicerp.numbering;

/**
 * Anything that gets a running document number: GRN, issue, body batch, spray dryer run ...
 * Enums implement it for free - every enum already has name(); they only add getPrefix().
 */
public interface DocumentKind {

    /** Key of the counter row, e.g. "GRN", "BODY_BATCH". Never change it once documents exist. */
    String name();

    /** Short code inside the number, e.g. "GRN" in UNIT-1/GRN/26-27/0001. */
    String getPrefix();
}
