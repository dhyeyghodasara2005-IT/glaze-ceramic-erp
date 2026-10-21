package com.ceramicerp.master.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UnitOfMeasureRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{1,10}$", message = "1-10 letters, digits, - or _")
        String code,

        @NotBlank @Size(max = 40)
        String name,

        /** 0 = whole numbers only (BOX), 3 = grams in KG */
        @NotNull @Min(0) @Max(4)
        Integer decimals,

        Boolean active
) {
}
