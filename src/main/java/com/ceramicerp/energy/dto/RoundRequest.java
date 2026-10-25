package com.ceramicerp.energy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A reading round: the readings of one shift (and the day's once-a-day meters), typed in one go.
 * Lines with nothing typed are left as they are.
 *
 * @param shiftId null = only the once-a-day meters
 */
public record RoundRequest(@NotNull Long plantId, @NotNull LocalDate date, Long shiftId,
                           @NotNull List<@Valid Line> lines) {

    /**
     * @param reading the number on the meter (counters)
     * @param used    the amount used (coal, diesel ...), typed as it is
     */
    public record Line(@NotNull Long meterId, @PositiveOrZero BigDecimal reading, @PositiveOrZero BigDecimal used,
                       Boolean restarted, @Size(max = 255) String note) {
    }
}
