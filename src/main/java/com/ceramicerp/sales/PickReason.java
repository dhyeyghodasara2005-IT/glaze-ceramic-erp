package com.ceramicerp.sales;

/** Why the loading plan took boxes from this stock row: the first rule that set it apart from the others. */
public enum PickReason {
    /** No other boxes of this design and grade are free. */
    ONLY_STOCK,
    /** In front: the other boxes need digging behind newer pallets. */
    IN_FRONT,
    /** Takes everything in the pillar, so the pillar becomes free. */
    CLEARS_PILLAR,
    /** Takes every free box of this stock row. */
    WHOLE_STOCK,
    /** Packed first (first in, first out). */
    OLDEST,
    /** Has the most boxes, so the line needs fewer pillars. */
    MOST_BOXES,
    /** Nearest to the loading point. */
    NEAREST,
    /** Added by the loader at loading. */
    ADDED
}
