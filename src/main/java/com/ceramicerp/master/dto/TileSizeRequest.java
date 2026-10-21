package com.ceramicerp.master.dto;

import com.ceramicerp.common.ValidationPatterns;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * What the screen sends to create or edit a size.
 * sqmPerBox is NOT here: the server calculates it, so nobody can type a wrong value.
 */
public record TileSizeRequest(
        @NotBlank @Pattern(regexp = ValidationPatterns.CODE, message = "2-20 letters, digits, - or _")
        String code,

        @NotBlank @Size(max = 60)
        String name,

        @NotNull @Min(1) @Max(5000)
        Integer lengthMm,

        @NotNull @Min(1) @Max(5000)
        Integer widthMm,

        @NotNull @DecimalMin("0.1") @DecimalMax("99.9") @Digits(integer = 3, fraction = 1)
        BigDecimal thicknessMm,

        @NotNull @Min(1) @Max(100)
        Integer pcsPerBox,

        @DecimalMin("0") @Digits(integer = 4, fraction = 2)
        BigDecimal boxWeightKg,

        @Min(1) @Max(1000)
        Integer boxesPerPallet,

        /** null = keep current value (true for new sizes) */
        Boolean active
) {
}