package com.ceramicerp.store;

/** Why a stock adjustment was made. */
public enum AdjustmentReason {
    /** Stock already in the godown on the day you start using Glaze. */
    OPENING,
    /** Correction after counting / weighing the actual stock. */
    PHYSICAL_COUNT,
    /** Spoiled, wet, broken or lost material. */
    DAMAGE,
    OTHER
}
