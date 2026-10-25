package com.ceramicerp.maintenance.dto;

import com.ceramicerp.maintenance.PmJobStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * One time a preventive job was done.
 *
 * @param latest true for the newest record of its job: only that one can be cancelled
 */
public record PmJobResponse(Long id, String jobNo, LocalDate doneOn, LocalDate wasDue, int daysLate, String doneBy,
                            Integer minutes, Integer stopMinutes, String findings, PmJobStatus status,
                            List<SpareUsed> spares, BigDecimal spareCost, Long issueDocumentId, String issueDocumentNo,
                            String createdBy, Instant createdAt, Instant cancelledAt, String cancelledBy,
                            String cancelReason, boolean latest) {
}
