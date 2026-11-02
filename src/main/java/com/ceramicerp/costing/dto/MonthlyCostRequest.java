package com.ceramicerp.costing.dto;

import com.ceramicerp.costing.MonthlyCostHead;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/** A month's costs: only the heads sent change. */
public record MonthlyCostRequest(@NotNull Long plantId, @NotNull YearMonth month, @NotNull List<@Valid Line> lines) {

    /** @param amount null = this head has no cost this month */
    public record Line(@NotNull MonthlyCostHead head, @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal amount,
                       @Size(max = 255) String note) {
    }
}
