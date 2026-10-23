package com.ceramicerp.fg.dto;

import java.util.List;

/** A stock row and every movement that made it what it is, oldest first. */
public record FgLedger(FgStockRow stock, List<FgMoveResponse> moves) {
}
