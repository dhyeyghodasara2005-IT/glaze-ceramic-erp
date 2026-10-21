package com.ceramicerp.master.dto;

import com.ceramicerp.common.ValidationPatterns;
import com.ceramicerp.master.MachineType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MachineRequest(
        @NotNull(message = "Select a plant")
        Long plantId,

        @NotBlank @Pattern(regexp = ValidationPatterns.CODE, message = "2-20 letters, digits, - or _")
        String code,

        @NotBlank @Size(max = 120)
        String name,

        /** JSON text like "KILN". An unknown value is rejected with 400 before reaching the service. */
        @NotNull(message = "Select a type")
        MachineType type,

        @DecimalMin("0") @Digits(integer = 12, fraction = 2)
        BigDecimal capacity,

        @Size(max = 30)
        String capacityUnit,

        @Size(max = 80)
        String make,

        @Size(max = 80)
        String model,

        LocalDate installedOn,

        Boolean active
) {
}
