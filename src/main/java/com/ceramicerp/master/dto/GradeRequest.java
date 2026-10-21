package com.ceramicerp.master.dto;

import com.ceramicerp.common.ValidationPatterns;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** What the screen sends to create or edit a grade. */
public record GradeRequest(
        @NotBlank @Pattern(regexp = ValidationPatterns.CODE, message = "2-20 letters, digits, - or _")
        String code,

        @NotBlank @Size(max = 60)
        String name,

        /** 1 = best. Lists are sorted by this. */
        @NotNull @Min(0) @Max(999)
        Integer sortOrder,

        /** false for Reject / Broken: they can't be put on a sales order. */
        @NotNull
        Boolean sellable,

        Boolean active
) {
}
