package com.ceramicerp.fg.dto;

import java.time.Instant;

/**
 * When boxes first and last came into a stock row. The first arrival says how old the boxes are (first in, first
 * out); the last says which pallets stand in front of a pillar (the newest pallets go in front).
 */
public record StockArrival(Long stockId, Instant firstIn, Instant lastIn) {
}
