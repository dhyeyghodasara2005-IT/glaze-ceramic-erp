package com.ceramicerp.production.dto;

import com.ceramicerp.production.LineStage;
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
 * One shift of a lot at the glaze line, the kiln or the polishing line (saved as DRAFT). Readings of
 * other stages are ignored: a kiln entry has no glaze weight, a glaze entry has no firing temperature.
 */
public record LineEntryRequest(
        @NotNull(message = "Select a plant")
        Long plantId,

        @NotNull
        LineStage stage,

        @NotNull(message = "Select the design lot")
        Long lotId,

        @NotNull(message = "Enter the production date")
        LocalDate prodDate,

        @NotNull(message = "Select the shift")
        Long shiftId,

        /** The glaze line, the kiln or the polishing line. */
        @NotNull(message = "Select the machine")
        Long machineId,

        /** Digital printer (glaze stage). */
        Long printerId,

        @NotNull(message = "Enter the tiles in") @Min(1) @Max(1_000_000)
        Integer tilesIn,

        @Min(0) @Max(1_000_000)
        Integer rejects,

        // glaze & print
        @DecimalMin("0") @DecimalMax("99999") @Digits(integer = 6, fraction = 1)
        BigDecimal engobeGpt,

        @DecimalMin("0") @DecimalMax("99999") @Digits(integer = 6, fraction = 1)
        BigDecimal glazeGpt,

        @DecimalMin("0") @DecimalMax("3000") @Digits(integer = 5, fraction = 1)
        BigDecimal glazeDensity,

        @DecimalMin("0") @Digits(integer = 6, fraction = 2)
        BigDecimal inkLitres,

        // kiln
        @DecimalMin("0") @DecimalMax("1500") @Digits(integer = 5, fraction = 1)
        BigDecimal maxTemp,

        @DecimalMin("0") @DecimalMax("999") @Digits(integer = 4, fraction = 1)
        BigDecimal cycleMin,

        @DecimalMin("0") @Digits(integer = 10, fraction = 2)
        BigDecimal gasScm,

        // polishing
        @DecimalMin("0") @Digits(integer = 10, fraction = 2)
        BigDecimal powerKwh,

        @Size(max = 500)
        String remarks
) {
}
