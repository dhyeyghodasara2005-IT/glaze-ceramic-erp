package com.ceramicerp.production.dto;

import com.ceramicerp.production.LotStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A design lot with everything the lot page shows: totals, its entries at every stage, the boxes
 * it packed, and the trace back through the spray runs to the body batches.
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
        Long printerId,
        String printerCode,
        String printerName,
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
        /** What the press did in this lot's shifts, on its line (the shift match, see LotPressMatch). */
        PressWork pressWork,
        /** glazed -> fired -> polished -> packed, with tiles waiting between stages */
        LotFlow flow,
        /** The press entries of this lot's shifts. */
        List<PressEntrySummary> entries,
        List<LineEntrySummary> glazeEntries,
        List<LineEntrySummary> kilnEntries,
        List<LotCheckResponse> checks,
        List<LineEntrySummary> polishEntries,
        List<PackEntrySummary> packEntries,
        /** boxes per grade + shade + caliber, best grade first */
        List<PackedBatch> packed,
        Quality quality,
        List<TraceRun> trace
) {
    /**
     * Posted press entries of the lot's shifts, added up. When a design changes in the middle of a shift, the lot
     * before and the lot after both show that shift's pressing.
     *
     * @param lossPct (press rejects + dryer rejects) / pressed x 100
     */
    public record PressWork(long entries, long pressed, long pressRejects, long dryerRejects, long good,
                            BigDecimal lossPct, BigDecimal powderTon, BigDecimal untracedTon) {
    }

    /**
     * What the cooling area expected against what sorting found.
     *
     * @param topGradeName   the best sellable grade (Premium)
     * @param expectedTopPct from the latest quality check that gave one
     * @param actualTopPct   top-grade pieces / tiles sorted so far
     */
    public record Quality(String topGradeName, BigDecimal expectedTopPct, BigDecimal actualTopPct) {
    }

    /** "12 T came from spray run SD/0003 (silo SILO-1), which used body batches BB/0011 and BB/0012." */
    public record TraceRun(Long runId, String runNo, String siloCode, LocalDate prodDate, BigDecimal tonnes,
                           List<BatchRef> batches) {
    }

    public record BatchRef(Long id, String batchNo) {
    }
}
