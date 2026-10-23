package com.ceramicerp.fg.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PillarUpdateRequest(
        @Min(1) @Max(9999)
        Integer capacityPallets,

        @NotNull
        Boolean active
) {
}
