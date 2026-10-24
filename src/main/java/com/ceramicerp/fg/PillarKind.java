package com.ceramicerp.fg;

/** What a pillar is used for. */
public enum PillarKind {
    /** One kind of box (design, batch, grade, shade, caliber) per pillar. */
    STORAGE,
    /** Small leftovers (less than a pallet), near the loading point, picked by hand. */
    REMAINDER,
    /** A lane at the loading point where a truck's load is brought in advance (step 6f). Never suggested for new pallets. */
    STAGING
}
