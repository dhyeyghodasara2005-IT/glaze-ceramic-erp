package com.ceramicerp.sales;

/**
 * DRAFT (being entered) -> CONFIRMED (boxes picked and kept for it; being loaded) -> DISPATCHED (on the truck).
 * A confirmed order can go back to DRAFT (reopen) or be CANCELLED; a dispatch can be undone (back to CONFIRMED).
 */
public enum SalesOrderStatus {
    DRAFT,
    CONFIRMED,
    DISPATCHED,
    CANCELLED
}
