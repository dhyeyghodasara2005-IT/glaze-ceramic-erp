package com.ceramicerp.master.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * What the screen sends to create or edit a design.
 * Category, size and finish are sent as ids (picked from dropdowns).
 */
public record DesignRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{2,30}$", message = "2-30 letters, digits, - or _")
        String code,

        @NotBlank @Size(max = 120)
        String name,

        @NotNull(message = "Select a category")
        Long categoryId,

        @NotNull(message = "Select a size")
        Long sizeId,

        @NotNull(message = "Select a finish")
        Long finishId,

        @NotNull @Min(1) @Max(99)
        Integer faces,

        /** Optional. JSON format "2026-10-22". */
        LocalDate launchDate,

        @Size(max = 255)
        String description,

        Boolean active
) {
}
