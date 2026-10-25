package com.ceramicerp.lab.dto;

import com.ceramicerp.lab.LabVerdict;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** The lab's result for one material of one GRN. Saving it again (while the GRN is a draft) corrects it. */
public record LabTestRequest(@NotNull Long documentId,
                             @NotNull Long materialId,
                             @NotNull LocalDate testedOn,
                             @Size(max = 150) String testedBy,
                             @NotNull LabVerdict verdict,
                             @Size(max = 500) String remarks,
                             @NotNull List<@Valid Result> results) {

    public record Result(@NotNull Long parameterId, @NotNull @Digits(integer = 9, fraction = 3) BigDecimal value) {
    }
}
