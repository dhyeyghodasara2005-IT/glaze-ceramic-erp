package com.ceramicerp.production;

import com.ceramicerp.numbering.DocumentKind;

/** Production documents that get a running number: UNIT-1/BB/26-27/0001. */
public enum ProductionDocType implements DocumentKind {
    /** One ball mill charge. */
    BODY_BATCH("BB"),
    /** One spray dryer run. */
    SPRAY_RUN("SD");

    private final String prefix;

    ProductionDocType(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public String getPrefix() {
        return prefix;
    }
}
