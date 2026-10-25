package com.ceramicerp.maintenance.dto;

import com.ceramicerp.maintenance.DueStatus;
import com.ceramicerp.maintenance.PmKind;

import java.time.LocalDate;

/** One preventive job in the due list. */
public record PmTaskSummary(Long id, MachineRef machine, String title, PmKind kind, int everyDays, boolean needsStop,
                            LocalDate nextDue, LocalDate lastDone, boolean active, DueStatus due, int daysOverdue) {
}
