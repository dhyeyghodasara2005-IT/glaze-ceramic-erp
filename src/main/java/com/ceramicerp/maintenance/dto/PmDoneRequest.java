package com.ceramicerp.maintenance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** A preventive job was done: when, by whom, what was found, and the spares used. */
public record PmDoneRequest(
        @NotNull(message = "Enter when it was done")
        LocalDate doneOn,

        @Size(max = 150)
        String doneBy,

        @Min(0) @Max(100_000)
        Integer minutes,

        @Min(0) @Max(100_000)
        Integer stopMinutes,

        @Size(max = 500)
        String findings,

        @Size(max = 50)
        List<@Valid SpareLine> spares
) {
}
