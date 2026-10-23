package com.ceramicerp.fg;

/** Why boxes were written off. A fixed list, so breakage can be counted by cause later. */
public enum BreakageReason {
    /** Forklift or stacking. */
    HANDLING,
    /** While loading a truck. */
    LOADING,
    /** In storage: a stack collapsed, water, ... */
    STORAGE,
    OTHER
}
