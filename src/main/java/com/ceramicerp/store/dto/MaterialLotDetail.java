package com.ceramicerp.store.dto;

import com.ceramicerp.store.MaterialLot;
import com.ceramicerp.store.MaterialLotDraw;
import com.ceramicerp.store.StoreDocType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** A lot and every document that took from it, newest first. */
public record MaterialLotDetail(MaterialLotRow lot, List<Use> uses) {

    public record Use(Long id, Long documentId, String docNo, StoreDocType docType, LocalDate date, String issuedTo,
                      String machineCode, BigDecimal quantity) {
    }

    public static MaterialLotDetail from(MaterialLot lot, List<MaterialLotDraw> draws) {
        return new MaterialLotDetail(MaterialLotRow.from(lot), draws.stream().map(d -> {
            var t = d.getTransaction();
            var doc = t.getDocument();
            return new Use(d.getId(), doc.getId(), t.getDocNo(), t.getDocType(), t.getTxnDate(), doc.getIssuedTo(),
                    doc.getMachine() == null ? null : doc.getMachine().getCode(), d.getQuantity());
        }).toList());
    }
}
