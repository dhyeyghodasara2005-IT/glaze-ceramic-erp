package com.ceramicerp.maintenance.dto;

import com.ceramicerp.maintenance.BreakdownCategory;
import com.ceramicerp.maintenance.BreakdownStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @param downtimeMinutes for an open breakdown: the minutes so far
 * @param spareCost       the value of the spares at store average cost (from the store issue)
 */
public record BreakdownResponse(
        Long id,
        Long plantId,
        String plantCode,
        String breakdownNo,
        MachineRef machine,
        LocalDateTime stoppedAt,
        String symptom,
        BreakdownStatus status,
        LocalDateTime restartedAt,
        int downtimeMinutes,
        BreakdownCategory category,
        String failedPart,
        String cause,
        String actionTaken,
        String fixedBy,
        List<Spare> spares,
        Long issueDocumentId,
        String issueDocumentNo,
        BigDecimal spareCost,
        String reportedBy,
        Instant reportedAt,
        Instant closedAt,
        String closedBy,
        Instant cancelledAt,
        String cancelledBy,
        String cancelReason,
        Long version
) {
    public record Spare(int lineNo, Long materialId, String materialCode, String materialName, String unit,
                        Long locationId, String locationCode, BigDecimal quantity, BigDecimal value) {
    }
}
