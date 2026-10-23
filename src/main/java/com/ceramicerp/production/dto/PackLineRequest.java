package com.ceramicerp.production.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** One row of a pack entry: so many boxes of this grade, shade and caliber. */
public record PackLineRequest(
        @NotNull(message = "Select the grade")
        Long gradeId,

        @NotBlank(message = "Enter the shade") @Size(max = 10)
        String shade,

        @NotBlank(message = "Enter the caliber") @Size(max = 10)
        String caliber,

        @NotNull(message = "Enter the boxes") @Min(1) @Max(100_000)
        Integer boxes
) {
}
