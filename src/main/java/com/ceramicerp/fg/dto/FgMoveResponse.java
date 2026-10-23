package com.ceramicerp.fg.dto;

import com.ceramicerp.fg.FgMove;
import com.ceramicerp.fg.FgSource;

import java.time.Instant;
import java.time.LocalDate;

public record FgMoveResponse(Long id, LocalDate moveDate, int boxes, int balanceBoxes, FgSource source, Long sourceId,
                             String sourceNo, boolean reversal, Instant createdAt, String createdBy) {

    public static FgMoveResponse from(FgMove m) {
        return new FgMoveResponse(m.getId(), m.getMoveDate(), m.getBoxes(), m.getBalanceBoxes(), m.getSource(),
                m.getSourceId(), m.getSourceNo(), m.isReversal(), m.getCreatedAt(), m.getCreatedBy());
    }
}
