package com.ceramicerp.fg.dto;

import com.ceramicerp.fg.PillarKind;
import com.ceramicerp.fg.PillarZone;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** One pillar's settings. zone and kind left empty = unchanged; walkOrder empty = not set. */
public record PillarUpdateRequest(
        @Min(1) @Max(9999)
        Integer capacityPallets,

        @NotNull
        Boolean active,

        PillarZone zone,

        @Min(1) @Max(9999)
        Integer walkOrder,

        PillarKind kind
) {
}
