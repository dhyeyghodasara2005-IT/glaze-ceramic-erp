package com.ceramicerp.maintenance;

/** What kind of routine work a preventive job is. */
public enum PmKind {
    LUBRICATION,
    INSPECTION,
    CLEANING,
    ADJUSTMENT,
    /** Filters, oil, wear parts changed on a schedule. */
    REPLACEMENT,
    CALIBRATION,
    OTHER
}
