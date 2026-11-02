package com.ceramicerp.labour.dto;

import com.ceramicerp.labour.AttendanceStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A day's marks for some workers, saved in one go.
 *
 * @param shiftId the shift they worked (optional)
 */
public record AttendanceRequest(@NotNull Long plantId, @NotNull LocalDate date, Long shiftId,
                                @NotNull List<@Valid Line> lines) {

    /** @param status null = take the mark back (the day is not marked) */
    public record Line(@NotNull Long workerId, AttendanceStatus status,
                       @DecimalMin("0") @DecimalMax("16") @Digits(integer = 2, fraction = 1) BigDecimal otHours,
                       @Size(max = 255) String note) {
    }
}
