package com.ceramicerp.energy;

/** How often a meter is read. */
public enum ReadEvery {
    /** Once a day, the next morning, with the day's last shift: the reading closes that day. */
    DAY,
    /** After each shift: the reading belongs to that shift. */
    SHIFT
}
