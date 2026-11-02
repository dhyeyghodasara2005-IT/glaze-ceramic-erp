package com.ceramicerp.store.dto;

import com.ceramicerp.store.MaterialLot;

import java.math.BigDecimal;

/** A lot on a document's screen, with how much of it this document moved. */
public record DocumentLot(MaterialLotRow lot, BigDecimal quantity) {

    public static DocumentLot of(MaterialLot lot, BigDecimal quantity) {
        return new DocumentLot(MaterialLotRow.from(lot), quantity);
    }
}
