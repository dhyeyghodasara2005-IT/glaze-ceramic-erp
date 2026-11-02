package com.ceramicerp.costing;

/**
 * Costs that come as one amount a month and aren't counted anywhere else in Glaze. Spares from the store and workers'
 * days aren't here: the store issues and the attendance already count them.
 */
public enum MonthlyCostHead {
    /** The fixed and demand charges on the power bill (the units come from the meter). */
    POWER_FIXED,
    /** The fixed or minimum charges on the gas bill. */
    GAS_FIXED,
    STAFF_SALARY,
    /** Repairs by outside firms (spares from the store are counted already). */
    REPAIRS_OUTSIDE,
    ADMIN,
    INTEREST,
    DEPRECIATION,
    OTHER
}
