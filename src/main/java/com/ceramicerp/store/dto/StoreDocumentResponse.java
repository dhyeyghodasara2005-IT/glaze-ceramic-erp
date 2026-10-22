package com.ceramicerp.store.dto;

import com.ceramicerp.store.AdjustmentReason;
import com.ceramicerp.store.DocStatus;
import com.ceramicerp.store.StoreDocType;
import com.ceramicerp.store.StoreDocument;
import com.ceramicerp.store.StoreDocumentLine;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** A full store document with its lines, for the document screen. */
public record StoreDocumentResponse(
        Long id,
        Long plantId,
        String plantCode,
        StoreDocType docType,
        String docNo,
        LocalDate docDate,
        DocStatus status,
        Long supplierId,
        String supplierCode,
        String supplierName,
        String supplierInvoiceNo,
        LocalDate supplierInvoiceDate,
        String vehicleNo,
        String weighbridgeSlipNo,
        Long machineId,
        String machineCode,
        String machineName,
        String issuedTo,
        AdjustmentReason reason,
        String remarks,
        BigDecimal totalValue,
        Instant postedAt,
        String postedBy,
        Instant cancelledAt,
        String cancelledBy,
        String cancelReason,
        Instant createdAt,
        String createdBy,
        Long version,
        List<Line> lines
) {
    public record Line(
            Long id,
            int lineNo,
            Long materialId,
            String materialCode,
            String materialName,
            String unitCode,
            int unitDecimals,
            Long locationId,
            String locationCode,
            String locationName,
            BigDecimal quantity,
            BigDecimal receivedQty,
            BigDecimal rejectedQty,
            BigDecimal rate,
            BigDecimal value,
            BigDecimal moisturePct,
            String remarks
    ) {
        static Line from(StoreDocumentLine l) {
            var m = l.getMaterial();
            var loc = l.getLocation();
            return new Line(l.getId(), l.getLineNo(), m.getId(), m.getCode(), m.getName(), m.getUnit().getCode(),
                    m.getUnit().getDecimals(), loc.getId(), loc.getCode(), loc.getName(), l.getQuantity(),
                    l.getReceivedQty(), l.getRejectedQty(), l.getRate(), l.getValue(), l.getMoisturePct(), l.getRemarks());
        }
    }

    /** Must be called inside a transaction. */
    public static StoreDocumentResponse from(StoreDocument d) {
        var s = d.getSupplier();
        var m = d.getMachine();
        return new StoreDocumentResponse(d.getId(), d.getPlant().getId(), d.getPlant().getCode(), d.getDocType(),
                d.getDocNo(), d.getDocDate(), d.getStatus(),
                s == null ? null : s.getId(), s == null ? null : s.getCode(), s == null ? null : s.getName(),
                d.getSupplierInvoiceNo(), d.getSupplierInvoiceDate(), d.getVehicleNo(), d.getWeighbridgeSlipNo(),
                m == null ? null : m.getId(), m == null ? null : m.getCode(), m == null ? null : m.getName(),
                d.getIssuedTo(), d.getReason(), d.getRemarks(), d.getTotalValue(),
                d.getPostedAt(), d.getPostedBy(), d.getCancelledAt(), d.getCancelledBy(), d.getCancelReason(),
                d.getCreatedAt(), d.getCreatedBy(), d.getVersion(),
                d.getLines().stream().map(Line::from).toList());
    }
}
