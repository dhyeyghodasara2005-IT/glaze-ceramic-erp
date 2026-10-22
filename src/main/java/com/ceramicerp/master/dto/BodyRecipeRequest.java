package com.ceramicerp.master.dto;

import com.ceramicerp.common.ValidationPatterns;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record BodyRecipeRequest(
        @NotBlank @Pattern(regexp = ValidationPatterns.CODE, message = "2-20 letters, digits, - or _")
        String code,

        @NotBlank @Size(max = 80)
        String name,

        @Size(max = 255)
        String description,

        Boolean active,

        @NotEmpty(message = "Add at least one material") @Size(max = 30) @Valid
        List<Line> lines
) {
    public record Line(
            @NotNull(message = "Select a material")
            Long materialId,

            /** In the material's unit, per tonne of dry charge: 0.300 TON or 3 KG. */
            @NotNull(message = "Enter the quantity") @DecimalMin(value = "0", inclusive = false) @Digits(integer = 8, fraction = 4)
            BigDecimal qtyPerTon
    ) {
    }
}
