package com.ceramicerp.production.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One shift of a lot at the press and dryer (saved as DRAFT; posting is a separate call).
 * Powder used: leave powderTon empty and it is worked out as tiles pressed x green tile weight.
 */
public record PressEntryRequest(
        @NotNull(message = "Select a plant")
        Long plantId,

        @NotNull(message = "Select the design lot")
        Long lotId,

        @NotNull(message = "Enter the production date")
        LocalDate prodDate,

        @NotNull(message = "Select the shift")
        Long shiftId,

        Long dryerId,

        @NotNull(message = "Select the silo")
        Long siloId,

        @NotNull(message = "Enter the tiles pressed") @Min(1) @Max(1_000_000)
        Integer pressedCount,

        @Min(0) @Max(1_000_000)
        Integer pressRejects,

        @Min(0) @Max(1_000_000)
        Integer dryerRejects,

        @DecimalMin(value = "0", inclusive = false) @DecimalMax("200") @Digits(integer = 4, fraction = 2)
        BigDecimal greenWeightKg,

        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 7, fraction = 3)
        BigDecimal powderTon,

        @DecimalMin("0") @DecimalMax("9999") @Digits(integer = 5, fraction = 1)
        BigDecimal pressure,

        @DecimalMin("0") @DecimalMax("99") @Digits(integer = 3, fraction = 1)
        BigDecimal cyclesPerMin,

        @DecimalMin("0") @DecimalMax("99") @Digits(integer = 3, fraction = 2)
        BigDecimal thicknessMm,

        @DecimalMin("0") @DecimalMax("999") @Digits(integer = 4, fraction = 1)
        BigDecimal dryerTemp,

        @DecimalMin("0") @DecimalMax("20") @Digits(integer = 2, fraction = 2)
        BigDecimal dryerMoisture,

        @Size(max = 500)
        String remarks
) {
}
