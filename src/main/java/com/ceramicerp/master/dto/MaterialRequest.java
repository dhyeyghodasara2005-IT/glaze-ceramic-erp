package com.ceramicerp.master.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record MaterialRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{2,30}$", message = "2-30 letters, digits, - or _")
        String code,

        @NotBlank @Size(max = 120)
        String name,

        @NotNull(message = "Select a group")
        Long groupId,

        @NotNull(message = "Select a unit")
        Long unitId,

        @Pattern(regexp = "^[0-9]{4,8}$", message = "HSN code is 4 to 8 digits")
        String hsnCode,

        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
        BigDecimal gstRate,

        @DecimalMin("0") @Digits(integer = 11, fraction = 3)
        BigDecimal reorderLevel,

        @Size(max = 255)
        String description,

        Boolean active
) {
}
