package com.ceramicerp.fg.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** "Add pillars A1 to A20": a letter prefix and a number range. Codes that already exist are skipped. */
public record PillarBulkRequest(
        @NotBlank(message = "Enter the letter") @Pattern(regexp = "[A-Za-z]{1,3}", message = "1 to 3 letters")
        String prefix,

        @NotNull(message = "Enter the first number") @Min(1) @Max(999)
        Integer from,

        @NotNull(message = "Enter the last number") @Min(1) @Max(999)
        Integer to,

        /** pallets that fit under each pillar (rows x 3 or 4 high); empty = not set */
        @Min(1) @Max(9999)
        Integer capacityPallets
) {
}
