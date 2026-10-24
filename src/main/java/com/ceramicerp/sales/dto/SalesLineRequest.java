package com.ceramicerp.sales.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** One product line of a slip. Design and grade may be empty in a draft; confirming needs both. */
public record SalesLineRequest(
        @NotBlank(message = "Enter the product description") @Size(max = 200)
        String description,

        Long designId,

        Long gradeId,

        @NotNull(message = "Enter the boxes") @Min(1) @Max(1_000_000)
        Integer boxes,

        @DecimalMin("0") @Digits(integer = 8, fraction = 2)
        BigDecimal mrpBox,

        @DecimalMin("0") @Digits(integer = 8, fraction = 2)
        BigDecimal mrpPiece,

        @Size(max = 30)
        String batchRef
) {
}
