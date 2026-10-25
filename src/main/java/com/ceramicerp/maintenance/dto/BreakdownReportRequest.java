package com.ceramicerp.maintenance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** "This machine stopped at this time, this is what we saw" - all a supervisor types. */
public record BreakdownReportRequest(
        @NotNull(message = "Select a plant")
        Long plantId,

        @NotNull(message = "Choose the machine")
        Long machineId,

        @NotNull(message = "Enter when it stopped")
        LocalDateTime stoppedAt,

        @NotBlank(message = "Say what happened") @Size(max = 255)
        String symptom
) {
}
