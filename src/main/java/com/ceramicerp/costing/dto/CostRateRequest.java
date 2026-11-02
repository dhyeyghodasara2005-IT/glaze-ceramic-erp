package com.ceramicerp.costing.dto;

import com.ceramicerp.energy.MeterKind;
import com.ceramicerp.energy.MeterUnit;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CostRateRequest(@NotNull Long plantId, @NotNull MeterKind kind, @NotNull MeterUnit unit,
                              @NotNull LocalDate validFrom,
                              @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 4) BigDecimal rate,
                              @Size(max = 255) String note) {
}
