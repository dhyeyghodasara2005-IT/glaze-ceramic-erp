package com.ceramicerp.production.dto;

import com.ceramicerp.production.PackEntry;
import com.ceramicerp.store.DocStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PackEntrySummary(
        Long id,
        String entryNo,
        Long lotId,
        String lotNo,
        String designName,
        LocalDate prodDate,
        String shiftCode,
        String machineCode,
        int tilesSorted,
        int boxes,
        int broken,
        BigDecimal packedSqm,
        DocStatus status
) {
    /** Needs lot, lot.design, shift and machine loaded. */
    public static PackEntrySummary from(PackEntry e, BigDecimal packedSqm) {
        return new PackEntrySummary(e.getId(), e.getEntryNo(), e.getLot().getId(), e.getLot().getLotNo(),
                e.getLot().getDesign().getName(), e.getProdDate(), e.getShift().getCode(), e.getMachine().getCode(),
                e.getTilesSorted(), e.getBoxes(), e.getBroken(), packedSqm, e.getStatus());
    }
}
