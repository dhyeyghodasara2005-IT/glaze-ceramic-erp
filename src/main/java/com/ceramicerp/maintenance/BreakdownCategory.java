package com.ceramicerp.maintenance;

/** What kind of fault stopped the machine: the first thing downtime figures are split by. */
public enum BreakdownCategory {
    MECHANICAL,
    ELECTRICAL,
    HYDRAULIC,
    PNEUMATIC,
    /** PLC, sensors, drives, the printer's electronics. */
    CONTROLS,
    /** The supply failed, not the machine. */
    POWER_CUT,
    /** Gas, compressed air, water. */
    UTILITY,
    /** Material or process: a jam, a broken tile stuck in the rollers, wrong settings. */
    PROCESS,
    OTHER
}
