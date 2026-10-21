package com.ceramicerp.master.dto;

import com.ceramicerp.common.ValidationPatterns;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** What the screen sends to create or edit a product category. */
public record ProductCategoryRequest(
        @NotBlank @Pattern(regexp = ValidationPatterns.CODE, message = "2-20 letters, digits, - or _")
        String code,

        @NotBlank @Size(max = 80)
        String name,

        /** Ceramic tiles are HSN 6907. Optional, 4-8 digits. */
        @Pattern(regexp = "^[0-9]{4,8}$", message = "HSN code is 4 to 8 digits")
        String hsnCode,

        /** GST in percent, e.g. 18.00 */
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
        BigDecimal gstRate,

        Boolean active
) {
}
