package com.ceramicerp.production;

/** Where a ball mill charge is in its life. */
public enum BodyBatchStatus {
    /** Being filled in; nothing has happened yet. */
    DRAFT,
    /** In the mill: raw material has left the store (an issue was made). */
    CHARGED,
    /** Milled, slip tested and sitting in a slip tank, waiting for the spray dryer. */
    DISCHARGED,
    /** Used up by the spray dryer. */
    CONSUMED,
    /** Cancelled: its store issue was reversed. */
    CANCELLED
}
