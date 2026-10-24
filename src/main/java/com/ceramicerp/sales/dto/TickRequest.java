package com.ceramicerp.sales.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** What the loader actually loaded from one plan row; null takes the tick back. */
public record TickRequest(@Min(0) @Max(1_000_000) Integer loadedBoxes) {
}
