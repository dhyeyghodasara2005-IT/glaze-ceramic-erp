package com.ceramicerp.master.dto;

import com.ceramicerp.common.ValidationPatterns;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The new machine that takes an old one's place. Plant and type are the old machine's, so they aren't asked.
 * The code may be the old one's: the old machine is then renamed (PRESS-1 -> PRESS-1-OLD).
 */
public record MachineReplaceRequest(
        @NotBlank @Pattern(regexp = ValidationPatterns.CODE, message = "2-20 letters, digits, - or _")
        String code,

        @NotBlank @Size(max = 120)
        String name,

        @DecimalMin("0") @Digits(integer = 12, fraction = 2)
        BigDecimal capacity,

        @Size(max = 30)
        String capacityUnit,

        @Size(max = 80)
        String make,

        @Size(max = 80)
        String model,

        LocalDate installedOn
) {
}
