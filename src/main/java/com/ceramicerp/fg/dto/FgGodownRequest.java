package com.ceramicerp.fg.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FgGodownRequest(
        @NotNull(message = "Select a plant")
        Long plantId,

        @NotBlank(message = "Enter a code") @Size(max = 20)
        @Pattern(regexp = "[A-Za-z0-9-]+", message = "Letters, digits and - only")
        String code,

        @NotBlank(message = "Enter a name") @Size(max = 80)
        String name,

        Boolean active
) {
}
