package com.ceramicerp.sales;

import com.ceramicerp.numbering.DocumentKind;

/** Numbers the sales module gives out. */
public enum SalesDocKind implements DocumentKind {
    /** The dispatch / gate pass number, given when a loading is confirmed: UNIT-1/DP/26-27/0001. */
    DISPATCH("DP");

    private final String prefix;

    SalesDocKind(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public String getPrefix() {
        return prefix;
    }
}
