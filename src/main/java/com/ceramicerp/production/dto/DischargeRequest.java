package com.ceramicerp.production.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Milling finished: the slip test results and the tank it went into.
 * Can be sent again to correct the results until the spray dryer has used the batch up.
 */
public record DischargeRequest(
        @NotNull(message = "Enter when milling ended")
        LocalDateTime millingEnd,

        @NotNull(message = "Select the slip tank")
        Long slipTankId,

        /** Litre weight, g/L. GVT slip is usually around 1690-1720. */
        @NotNull(message = "Enter the litre weight") @DecimalMin("1000") @DecimalMax("2500") @Digits(integer = 4, fraction = 1)
        BigDecimal slipDensity,

        @DecimalMin("0") @DecimalMax("999") @Digits(integer = 3, fraction = 1)
        BigDecimal slipViscosity,

        @NotNull(message = "Enter the residue") @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
        BigDecimal slipResidue
) {
}
