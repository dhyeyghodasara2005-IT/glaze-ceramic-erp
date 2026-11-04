package com.ceramicerp.production.dto;

import com.ceramicerp.production.PressEntry;
import com.ceramicerp.store.DocStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PressEntrySummary(
        Long id,
        String entryNo,
        LocalDate prodDate,
        String shiftCode,
        String pressCode,
        String siloCode,
        int pressedCount,
        int pressRejects,
        int dryerRejects,
        int goodCount,
        BigDecimal powderTon,
        DocStatus status
) {
    /** Needs shift, press and silo loaded. */
    public static PressEntrySummary from(PressEntry e) {
        return new PressEntrySummary(e.getId(), e.getEntryNo(), e.getProdDate(), e.getShift().getCode(), e.getPress().getCode(),
                e.getSilo().getCode(), e.getPressedCount(), e.getPressRejects(), e.getDryerRejects(), e.goodCount(),
                e.getPowderTon(), e.getStatus());
    }
}
