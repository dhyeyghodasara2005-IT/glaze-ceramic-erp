package com.ceramicerp.production.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Create / edit a spray dryer run while it is a DRAFT. */
public record SprayRunRequest(
        @NotNull(message = "Select a plant")
        Long plantId,

        @NotNull(message = "Enter the production date")
        LocalDate prodDate,

        @NotNull(message = "Select the shift")
        Long shiftId,

        @NotNull(message = "Select the spray dryer")
        Long sprayDryerId,

        @NotNull(message = "Select the silo")
        Long siloId,

        @NotNull(message = "Enter when the run started")
        LocalDateTime runStart,

        LocalDateTime runEnd,

        @NotNull(message = "Enter the powder produced") @DecimalMin(value = "0", inclusive = false) @Digits(integer = 7, fraction = 3)
        BigDecimal powderTon,

        @DecimalMin("0") @DecimalMax("20") @Digits(integer = 2, fraction = 2)
        BigDecimal powderMoisture,

        @DecimalMin("0") @Digits(integer = 10, fraction = 2)
        BigDecimal gasScm,

        @DecimalMin("0") @DecimalMax("1200") @Digits(integer = 4, fraction = 1)
        BigDecimal inletTemp,

        @DecimalMin("0") @DecimalMax("1200") @Digits(integer = 4, fraction = 1)
        BigDecimal outletTemp,

        @Size(max = 500)
        String remarks,

        @NotEmpty(message = "Select at least one body batch") @Size(max = 30) @Valid
        List<BatchUse> batches
) {
    /** finished = this batch's slip was used up in this run. */
    public record BatchUse(
            @NotNull Long bodyBatchId,
            boolean finished
    ) {
    }
}
