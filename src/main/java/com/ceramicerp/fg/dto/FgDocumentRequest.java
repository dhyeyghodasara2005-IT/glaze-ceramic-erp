package com.ceramicerp.fg.dto;

import com.ceramicerp.fg.FgDocType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record FgDocumentRequest(
        @NotNull(message = "Select a plant")
        Long plantId,

        @NotNull
        FgDocType docType,

        @NotNull(message = "Enter the date")
        LocalDate docDate,

        @NotEmpty(message = "Add at least one row") @Size(max = 500)
        List<@Valid FgLineRequest> lines,

        @Size(max = 500)
        String remarks
) {
}
