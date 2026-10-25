package com.ceramicerp.lab.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * The tests of one material, in the order the lab does them. A test left out is switched off (its old results keep
 * pointing to it).
 */
public record LabParametersRequest(@NotNull List<@Valid Line> parameters) {

    /**
     * @param id       an existing test, or null for a new one
     * @param minValue the acceptable range; either end may be left open
     */
    public record Line(Long id,
                       @NotBlank @Size(max = 10) String code,
                       @NotBlank @Size(max = 60) String name,
                       @Size(max = 15) String unit,
                       @Digits(integer = 9, fraction = 3) BigDecimal minValue,
                       @Digits(integer = 9, fraction = 3) BigDecimal maxValue) {
    }
}
