package com.ceramicerp.fg.dto;

import com.ceramicerp.fg.PillarKind;
import com.ceramicerp.fg.PillarZone;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Lay out a run of pillars at once: "A1 to A8: zone SLOW..FAST, walk order 8 down to 1".
 * Empty fields are left as they are.
 *
 * @param firstWalkOrder walk order of the first pillar ("from")
 * @param step           +1 when the walk order grows with the pillar number, -1 when it shrinks
 *                       (left of a loading point in the middle, A1..A8 run 8, 7, ... 1)
 */
public record PillarLayoutRequest(
        @NotBlank(message = "Enter the letter") @Pattern(regexp = "[A-Za-z]{1,3}", message = "1 to 3 letters")
        String prefix,

        @NotNull(message = "Enter the first number") @Min(1) @Max(999)
        Integer from,

        @NotNull(message = "Enter the last number") @Min(1) @Max(999)
        Integer to,

        PillarZone zone,

        PillarKind kind,

        @Min(1) @Max(9999)
        Integer firstWalkOrder,

        @Min(-1) @Max(1)
        Integer step
) {
}
