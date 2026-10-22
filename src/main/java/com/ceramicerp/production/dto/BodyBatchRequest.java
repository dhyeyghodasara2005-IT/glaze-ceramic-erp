package com.ceramicerp.production.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Create / edit a ball mill charge while it is a DRAFT. Charging the mill is a separate call. */
public record BodyBatchRequest(
        @NotNull(message = "Select a plant")
        Long plantId,

        @NotNull(message = "Enter the production date")
        LocalDate prodDate,

        @NotNull(message = "Select the shift")
        Long shiftId,

        @NotNull(message = "Select the ball mill")
        Long ballMillId,

        /** Optional: only used to fill in the materials. */
        Long recipeId,

        @NotNull(message = "Enter the dry charge") @DecimalMin(value = "0", inclusive = false) @Digits(integer = 7, fraction = 3)
        BigDecimal chargeTon,

        @DecimalMin("0") @Digits(integer = 11, fraction = 1)
        BigDecimal waterLitres,

        @NotNull(message = "Enter when milling started")
        LocalDateTime millingStart,

        @Size(max = 500)
        String remarks,

        @NotEmpty(message = "Add at least one material") @Size(max = 30) @Valid
        List<Line> lines
) {
    public record Line(
            @NotNull(message = "Select a material")
            Long materialId,

            @NotNull(message = "Select a godown")
            Long locationId,

            @NotNull(message = "Enter the quantity") @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 3)
            BigDecimal quantity
    ) {
    }
}
