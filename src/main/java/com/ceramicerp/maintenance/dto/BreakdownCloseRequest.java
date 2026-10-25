package com.ceramicerp.maintenance.dto;

import com.ceramicerp.maintenance.BreakdownCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** The machine runs again: what it was, what was done, and the spares used (taken out of the store). */
public record BreakdownCloseRequest(
        @NotNull(message = "Enter when it ran again")
        LocalDateTime restartedAt,

        @NotNull(message = "Choose the kind of fault")
        BreakdownCategory category,

        @Size(max = 100)
        String failedPart,

        @Size(max = 500)
        String cause,

        @NotBlank(message = "Say what was done") @Size(max = 500)
        String actionTaken,

        @Size(max = 150)
        String fixedBy,

        @Size(max = 50)
        List<@Valid SpareLine> spares
) {
}
