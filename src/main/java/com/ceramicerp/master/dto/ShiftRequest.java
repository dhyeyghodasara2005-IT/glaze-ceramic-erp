package com.ceramicerp.master.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

/** What the screen sends to create or edit a shift. Times arrive as "08:00". */
public record ShiftRequest(
        /** Short: A, B, C (one letter is fine here, unlike other codes). */
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9]{1,10}$", message = "1-10 letters or digits")
        String code,

        @NotBlank @Size(max = 40)
        String name,

        @NotNull(message = "Enter the start time")
        LocalTime startTime,

        /** Earlier than startTime = the shift ends the next morning. */
        @NotNull(message = "Enter the end time")
        LocalTime endTime,

        @NotNull @Min(0) @Max(99)
        Integer sortOrder,

        Boolean active
) {
}
