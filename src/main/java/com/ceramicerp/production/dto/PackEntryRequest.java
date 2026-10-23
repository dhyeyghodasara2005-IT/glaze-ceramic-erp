package com.ceramicerp.production.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** One shift of a lot through sorting and packing (saved as DRAFT). Pieces and totals are worked out by the server. */
public record PackEntryRequest(
        @NotNull(message = "Select a plant")
        Long plantId,

        @NotNull(message = "Select the design lot")
        Long lotId,

        @NotNull(message = "Enter the production date")
        LocalDate prodDate,

        @NotNull(message = "Select the shift")
        Long shiftId,

        /** The sorting line. */
        @NotNull(message = "Select the sorting line")
        Long machineId,

        @NotEmpty(message = "Add at least one row of boxes") @Size(max = 100)
        List<@Valid PackLineRequest> lines,

        /** Broken / rejected pieces: not boxed. */
        @Min(0) @Max(1_000_000)
        Integer broken,

        @Size(max = 500)
        String remarks
) {
}
