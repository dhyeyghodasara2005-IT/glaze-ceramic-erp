package com.ceramicerp.organization.dto;

import com.ceramicerp.common.ValidationPatterns;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PlantRequest(
        @NotNull
        Long companyId,

        @NotBlank @Pattern(regexp = ValidationPatterns.CODE, message = "2-20 letters, digits, - or _")
        String code,

        @NotBlank @Size(max = 150)
        String name,

        @Pattern(regexp = ValidationPatterns.GSTIN, message = "Invalid GSTIN")
        String gstin,

        @Size(max = 255)
        String addressLine,

        @Size(max = 100)
        String city,

        @Size(max = 100)
        String state,

        @Pattern(regexp = ValidationPatterns.PINCODE, message = "Invalid PIN code")
        String pincode,

        @DecimalMin("0") @Digits(integer = 10, fraction = 2)
        BigDecimal capacitySqmPerDay,

        Boolean active
) {
}
