package com.ceramicerp.maintenance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** A spare (or consumable) to take out of a store godown for a breakdown or a preventive job. */
public record SpareLine(
        @NotNull(message = "Choose the spare")
        Long materialId,

        @NotNull(message = "Choose the godown")
        Long locationId,

        @NotNull(message = "Enter the quantity") @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 3)
        BigDecimal quantity
) {
}
