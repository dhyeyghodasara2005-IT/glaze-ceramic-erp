package com.ceramicerp.production.dto;

import com.ceramicerp.production.CheckVerdict;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A quality check of a lot in the cooling area. Every reading is optional except samples and verdict. */
public record LotCheckRequest(
        @NotNull(message = "Select a plant")
        Long plantId,

        @NotNull(message = "Select the design lot")
        Long lotId,

        @NotNull(message = "Enter the date")
        LocalDate checkDate,

        @NotNull(message = "Select the shift")
        Long shiftId,

        @NotNull(message = "Enter the tiles checked") @Min(1) @Max(10_000)
        Integer samples,

        // bend can be + (convex) or - (concave)
        @DecimalMin("-99") @DecimalMax("99") @Digits(integer = 2, fraction = 2)
        BigDecimal centreBendMm,

        @DecimalMin("-99") @DecimalMax("99") @Digits(integer = 2, fraction = 2)
        BigDecimal edgeBendMm,

        @DecimalMin("-99") @DecimalMax("99") @Digits(integer = 2, fraction = 2)
        BigDecimal warpageMm,

        @DecimalMin("0") @DecimalMax("99999") @Digits(integer = 5, fraction = 2)
        BigDecimal lengthMm,

        @DecimalMin("0") @DecimalMax("99999") @Digits(integer = 5, fraction = 2)
        BigDecimal widthMm,

        @DecimalMin("0") @DecimalMax("999") @Digits(integer = 3, fraction = 2)
        BigDecimal thicknessMm,

        @NotNull(message = "Choose the verdict")
        CheckVerdict verdict,

        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
        BigDecimal expectedTopPct,

        @Size(max = 500)
        String remarks
) {
}
