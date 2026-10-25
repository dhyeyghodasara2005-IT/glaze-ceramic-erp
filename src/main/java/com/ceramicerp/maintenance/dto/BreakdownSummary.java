package com.ceramicerp.maintenance.dto;

import com.ceramicerp.maintenance.BreakdownCategory;
import com.ceramicerp.maintenance.BreakdownStatus;

import java.time.LocalDateTime;

/** One row of the breakdown list. For an open breakdown, downtimeMinutes is the time so far. */
public record BreakdownSummary(Long id, String breakdownNo, MachineRef machine, LocalDateTime stoppedAt, String symptom,
                               BreakdownStatus status, LocalDateTime restartedAt, int downtimeMinutes,
                               BreakdownCategory category, String failedPart, String reportedBy) {
}
