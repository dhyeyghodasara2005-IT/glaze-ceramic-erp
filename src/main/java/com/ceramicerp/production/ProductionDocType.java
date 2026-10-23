package com.ceramicerp.production;

import com.ceramicerp.numbering.DocumentKind;

/** Production documents that get a running number: UNIT-1/BB/26-27/0001. */
public enum ProductionDocType implements DocumentKind {
    /** One ball mill charge. */
    BODY_BATCH("BB"),
    /** One spray dryer run. */
    SPRAY_RUN("SD"),
    /** One run of one design on a press line (design lot). */
    LOT("LOT"),
    /** One shift of one lot at the press and dryer. */
    PRESS_ENTRY("PR"),
    /** One shift of one lot on the glaze line and digital printer. */
    GLAZE_ENTRY("GL"),
    /** One shift of one lot through the kiln. */
    KILN_ENTRY("KL"),
    /** One quality check of a lot in the cooling area. */
    LOT_CHECK("QC"),
    /** One shift of one lot on the polishing line. */
    POLISH_ENTRY("PL"),
    /** One shift of one lot through sorting and packing. */
    PACK_ENTRY("PK");

    private final String prefix;

    ProductionDocType(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public String getPrefix() {
        return prefix;
    }
}
