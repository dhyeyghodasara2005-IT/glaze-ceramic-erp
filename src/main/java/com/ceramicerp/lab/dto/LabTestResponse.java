package com.ceramicerp.lab.dto;

import com.ceramicerp.lab.LabVerdict;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** @param editable the GRN is still a draft, so the result may still change */
public record LabTestResponse(Long id, String testNo, Long plantId, LabSample sample, LocalDate testedOn, String testedBy,
                              LabVerdict verdict, int outOfRange, String remarks, List<Result> results, boolean editable,
                              String createdBy, Instant createdAt, String updatedBy, Instant updatedAt) {

    public record Result(Long parameterId, String code, String name, String unit, BigDecimal minValue,
                         BigDecimal maxValue, BigDecimal value, boolean withinRange) {
    }
}
