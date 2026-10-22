package com.ceramicerp.production.dto;

import com.ceramicerp.production.LotStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A design lot with everything the lot page shows: totals, its press entries, and the trace
 * back through the spray runs to the body batches.
 */
public record LotResponse(
        Long id,
        Long plantId,
        String plantCode,
        String lotNo,
        Long designId,
        String designCode,
        String designName,
        String sizeCode,
        String finishName,
        BigDecimal sqmPerTile,
        Long pressId,
        String pressCode,
        String pressName,
        LotStatus status,
        LocalDate openedDate,
        String openedShiftCode,
        String remarks,
        Instant openedAt,
        String openedBy,
        Instant closedAt,
        String closedBy,
        Instant cancelledAt,
        String cancelledBy,
        String cancelReason,
        Long version,
        Totals totals,
        /** pressed -> glazed -> fired, with tiles waiting between stages */
        LotFlow flow,
        List<PressEntrySummary> entries,
        List<LineEntrySummary> glazeEntries,
        List<LineEntrySummary> kilnEntries,
        List<TraceRun> trace
) {
    /**
     * @param lossPct (press rejects + dryer rejects) / pressed x 100
     */
    public record Totals(long entries, long pressed, long pressRejects, long dryerRejects, long good,
                         BigDecimal goodSqm, BigDecimal lossPct, BigDecimal powderTon, BigDecimal untracedTon) {
    }

    /** "12 T came from spray run SD/0003 (silo SILO-1), which used body batches BB/0011 and BB/0012." */
    public record TraceRun(Long runId, String runNo, String siloCode, LocalDate prodDate, BigDecimal tonnes,
                           List<BatchRef> batches) {
    }

    public record BatchRef(Long id, String batchNo) {
    }
}
