package com.ceramicerp.sales.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** The loader took boxes from a stock row the plan didn't name (a damaged pallet, or an easier one to reach). */
public record AddPickRequest(
        @NotNull(message = "Choose where the boxes came from")
        Long stockId,

        @NotNull(message = "Enter the boxes") @Min(1) @Max(1_000_000)
        Integer boxes
) {
}
