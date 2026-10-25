package com.ceramicerp.maintenance;

import com.ceramicerp.numbering.DocumentKind;

/** Numbers the maintenance module gives out. */
public enum MaintenanceDocKind implements DocumentKind {
    /** A breakdown report: UNIT-1/BD/26-27/0001. */
    BREAKDOWN("BD");

    private final String prefix;

    MaintenanceDocKind(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public String getPrefix() {
        return prefix;
    }
}
