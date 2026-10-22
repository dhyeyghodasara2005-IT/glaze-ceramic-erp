package com.ceramicerp.production.dto;

import com.ceramicerp.production.LineEntry;
import com.ceramicerp.production.LineStage;
import com.ceramicerp.store.DocStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LineEntrySummary(
        Long id,
        LineStage stage,
        String entryNo,
        Long lotId,
        String lotNo,
        String designName,
        LocalDate prodDate,
        String shiftCode,
        String machineCode,
        int tilesIn,
        int rejects,
        int tilesOut,
        BigDecimal gasScm,
        DocStatus status
) {
    /** Needs lot, lot.design, shift and machine loaded. */
    public static LineEntrySummary from(LineEntry e) {
        return new LineEntrySummary(e.getId(), e.getStage(), e.getEntryNo(), e.getLot().getId(), e.getLot().getLotNo(),
                e.getLot().getDesign().getName(), e.getProdDate(), e.getShift().getCode(), e.getMachine().getCode(),
                e.getTilesIn(), e.getRejects(), e.tilesOut(), e.getGasScm(), e.getStatus());
    }
}
