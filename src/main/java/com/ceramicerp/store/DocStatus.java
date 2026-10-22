package com.ceramicerp.store;

/**
 * Life of a store document:
 * DRAFT (editable, no stock effect) -> POSTED (stock changed, read-only) -> CANCELLED (stock reversed).
 */
public enum DocStatus {
    DRAFT,
    POSTED,
    CANCELLED
}
