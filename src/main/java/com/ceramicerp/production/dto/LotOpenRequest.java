package com.ceramicerp.production.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Start a design on a press line.
 *
 * @param closeCurrent true = if the press is running another lot, close it (the design is being changed)
 */
public record LotOpenRequest(
        @NotNull(message = "Select a plant")
        Long plantId,

        @NotNull(message = "Select the design")
        Long designId,

        @NotNull(message = "Select the press")
        Long pressId,

        @NotNull(message = "Enter the production date")
        LocalDate openedDate,

        @NotNull(message = "Select the shift")
        Long shiftId,

        @Size(max = 500)
        String remarks,

        boolean closeCurrent
) {
}
