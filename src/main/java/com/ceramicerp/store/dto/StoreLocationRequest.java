package com.ceramicerp.store.dto;

import com.ceramicerp.common.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StoreLocationRequest(
        @NotNull(message = "Select a plant")
        Long plantId,

        @NotBlank @Pattern(regexp = ValidationPatterns.CODE, message = "2-20 letters, digits, - or _")
        String code,

        @NotBlank @Size(max = 80)
        String name,

        Boolean active
) {
}
