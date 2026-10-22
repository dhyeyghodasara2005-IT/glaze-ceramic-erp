package com.ceramicerp.production;

/** A design lot is OPEN while its line runs it, CLOSED after the design changes. */
public enum LotStatus {
    OPEN,
    CLOSED,
    /** Opened by mistake; only possible while it has no posted press entries. */
    CANCELLED
}
