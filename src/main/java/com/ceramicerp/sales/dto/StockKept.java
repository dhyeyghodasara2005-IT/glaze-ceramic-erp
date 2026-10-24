package com.ceramicerp.sales.dto;

/** Boxes of one stock row kept for confirmed orders. */
public record StockKept(Long stockId, long boxes) {
}
