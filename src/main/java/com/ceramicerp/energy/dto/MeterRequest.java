package com.ceramicerp.energy.dto;

import com.ceramicerp.energy.MeterKind;
import com.ceramicerp.energy.MeterUnit;
import com.ceramicerp.energy.ReadEvery;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Sets up a meter, or changes one.
 *
 * @param unit      null = the kind's usual unit (gas may be SCM or m³)
 * @param machineId the machine it measures, if one (a kiln's gas meter, the spray dryer's coal)
 */
public record MeterRequest(
        @NotNull Long plantId,
        @NotBlank @Size(max = 30) String code,
        @NotBlank @Size(max = 100) String name,
        @NotNull MeterKind kind,
        MeterUnit unit,
        Boolean main,
        Long machineId,
        @NotNull ReadEvery readEvery,
        Integer sortOrder,
        Boolean active) {
}
