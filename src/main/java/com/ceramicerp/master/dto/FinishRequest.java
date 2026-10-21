package com.ceramicerp.master.dto;

import com.ceramicerp.common.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** What the screen sends to create or edit a finish. */
public record FinishRequest(
        @NotBlank @Pattern(regexp = ValidationPatterns.CODE, message = "2-20 letters, digits, - or _")
        String code,

        @NotBlank @Size(max = 60)
        String name,

        /** null = keep current value (true for new finishes) */
        Boolean active
) {
}