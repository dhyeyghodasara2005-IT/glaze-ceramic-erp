package com.ceramicerp.master;

/**
 * Kinds of machines / lines in a ceramic tile plant, in process order.
 * Fixed in code (like Permission) because production screens will behave differently per type,
 * e.g. kiln entries ask for temperature, press entries ask for cycles.
 * The frontend has the same list in api/types.ts with labels in en.ts / gu.ts.
 */
public enum MachineType {
    BALL_MILL,
    /** Underground / agitator tank that holds slip after the ball mill. */
    SLIP_TANK,
    SPRAY_DRYER,
    /** Holds spray-dried powder while it ages, before the press. */
    SILO,
    PRESS,
    DRYER,
    GLAZE_LINE,
    DIGITAL_PRINTER,
    /**
     * Compensator / box storage: parks tiles and sends them back on the line to fill gaps. It can sit anywhere
     * (after the dryer, before the kiln ...), so its place comes from the layout links. It has no entries.
     */
    COMPENSATOR,
    KILN,
    POLISHING_LINE,
    SORTING_LINE,
    PACKING_LINE,
    UTILITY,
    OTHER
}
