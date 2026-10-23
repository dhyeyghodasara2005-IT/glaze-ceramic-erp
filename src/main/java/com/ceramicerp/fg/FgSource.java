package com.ceramicerp.fg;

/** What made a finished-goods stock movement. Step 6 will add dispatch. */
public enum FgSource {
    /** A posted pack entry (in), or its cancellation (out). */
    PACK,
    /** Boxes already in the godown on go-live day (in). */
    OPENING,
    /** Boxes damaged in the godown (out). */
    BREAKAGE,
    /** Boxes re-checked: out of the old grade / shade / caliber / pillar, into the new one. */
    REGRADE
}
