package com.ceramicerp.maintenance.dto;

import com.ceramicerp.maintenance.PmKind;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * A preventive job. A new one may be set up for several machines at once (both presses); a change is for one
 * machine, so then machineIds holds exactly one.
 */
public record PmTaskRequest(
        @NotNull(message = "Select a plant")
        Long plantId,

        @NotEmpty(message = "Choose the machine") @Size(max = 50)
        List<@NotNull Long> machineIds,

        @NotBlank(message = "Say what the job is") @Size(max = 120)
        String title,

        @NotNull(message = "Choose the kind of job")
        PmKind kind,

        @NotNull(message = "Enter how often") @Min(1) @Max(3650)
        Integer everyDays,

        @Size(max = 1000)
        String instructions,

        Boolean needsStop,

        @NotNull(message = "Enter when it is due next")
        LocalDate nextDue,

        Boolean active
) {
}
