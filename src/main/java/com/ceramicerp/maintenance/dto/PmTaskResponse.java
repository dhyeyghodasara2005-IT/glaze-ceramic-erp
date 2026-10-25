package com.ceramicerp.maintenance.dto;

import com.ceramicerp.maintenance.DueStatus;
import com.ceramicerp.maintenance.PmKind;

import java.time.LocalDate;
import java.util.List;

/** A preventive job with its history, newest first. */
public record PmTaskResponse(Long id, Long plantId, String plantCode, MachineRef machine, String title, PmKind kind,
                             int everyDays, String instructions, boolean needsStop, LocalDate nextDue,
                             LocalDate lastDone, boolean active, DueStatus due, int daysOverdue,
                             List<PmJobResponse> history, String createdBy, Long version) {
}
