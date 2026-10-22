package com.ceramicerp.store.dto;

import com.ceramicerp.store.AdjustmentReason;
import com.ceramicerp.store.DocStatus;
import com.ceramicerp.store.StoreDocType;
import com.ceramicerp.store.StoreDocument;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One row in a document list (no lines - keeps the list fast). */
public record StoreDocumentSummary(
        Long id,
        StoreDocType docType,
        String docNo,
        LocalDate docDate,
        DocStatus status,
        String origin,
        String supplierName,
        String supplierInvoiceNo,
        String machineName,
        String issuedTo,
        AdjustmentReason reason,
        BigDecimal totalValue,
        String createdBy
) {
    public static StoreDocumentSummary from(StoreDocument d) {
        return new StoreDocumentSummary(d.getId(), d.getDocType(), d.getDocNo(), d.getDocDate(), d.getStatus(), d.getOrigin(),
                d.getSupplier() == null ? null : d.getSupplier().getName(), d.getSupplierInvoiceNo(),
                d.getMachine() == null ? null : d.getMachine().getName(), d.getIssuedTo(), d.getReason(),
                d.getTotalValue(), d.getCreatedBy());
    }
}
