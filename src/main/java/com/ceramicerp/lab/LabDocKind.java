package com.ceramicerp.lab;

import com.ceramicerp.numbering.DocumentKind;

/** Numbers the lab gives out. */
public enum LabDocKind implements DocumentKind {
    /** A raw-material test: UNIT-1/LAB/26-27/0001. */
    LAB_TEST("LAB");

    private final String prefix;

    LabDocKind(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public String getPrefix() {
        return prefix;
    }
}
