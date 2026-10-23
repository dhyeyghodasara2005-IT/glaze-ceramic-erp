package com.ceramicerp.production;

/** The checker's one-word call on a lot in the cooling area. */
public enum CheckVerdict {
    /** Flat and in size: expect normal sorting. */
    GOOD,
    /** Borderline: polishing and sorting should keep an eye on it. */
    WATCH,
    /** Out of limits: expect many downgrades. */
    PROBLEM
}
