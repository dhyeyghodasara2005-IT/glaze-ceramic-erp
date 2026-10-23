package com.ceramicerp.production.dto;

import com.ceramicerp.production.LineEntry;
import com.ceramicerp.production.LineStage;
import com.ceramicerp.store.DocStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record LineEntryResponse(
        Long id,
        Long plantId,
        String plantCode,
        LineStage stage,
        String entryNo,
        Long lotId,
        String lotNo,
        String designName,
        String pressCode,
        BigDecimal sqmPerTile,
        LocalDate prodDate,
        Long shiftId,
        String shiftCode,
        String shiftName,
        Long machineId,
        String machineCode,
        String machineName,
        Long printerId,
        String printerCode,
        DocStatus status,
        int tilesIn,
        int rejects,
        int tilesOut,
        BigDecimal sqmOut,
        BigDecimal engobeGpt,
        BigDecimal glazeGpt,
        BigDecimal glazeDensity,
        BigDecimal inkLitres,
        BigDecimal maxTemp,
        BigDecimal cycleMin,
        BigDecimal gasScm,
        /** kiln: gas per square metre fired - the kiln's main cost figure */
        BigDecimal gasPerSqm,
        BigDecimal powerKwh,
        String remarks,
        Instant postedAt,
        String postedBy,
        Instant cancelledAt,
        String cancelledBy,
        String cancelReason,
        Instant createdAt,
        String createdBy,
        Long version
) {
    public static LineEntryResponse from(LineEntry e, BigDecimal sqmPerTile, BigDecimal sqmOut, BigDecimal gasPerSqm) {
        var lot = e.getLot();
        var printer = e.getPrinter();
        return new LineEntryResponse(e.getId(), e.getPlant().getId(), e.getPlant().getCode(), e.getStage(), e.getEntryNo(),
                lot.getId(), lot.getLotNo(), lot.getDesign().getName(), lot.getPress().getCode(), sqmPerTile,
                e.getProdDate(), e.getShift().getId(), e.getShift().getCode(), e.getShift().getName(),
                e.getMachine().getId(), e.getMachine().getCode(), e.getMachine().getName(),
                printer == null ? null : printer.getId(), printer == null ? null : printer.getCode(), e.getStatus(),
                e.getTilesIn(), e.getRejects(), e.tilesOut(), sqmOut, e.getEngobeGpt(), e.getGlazeGpt(),
                e.getGlazeDensity(), e.getInkLitres(), e.getMaxTemp(), e.getCycleMin(), e.getGasScm(), gasPerSqm,
                e.getPowerKwh(), e.getRemarks(), e.getPostedAt(), e.getPostedBy(), e.getCancelledAt(), e.getCancelledBy(),
                e.getCancelReason(), e.getCreatedAt(), e.getCreatedBy(), e.getVersion());
    }
}
