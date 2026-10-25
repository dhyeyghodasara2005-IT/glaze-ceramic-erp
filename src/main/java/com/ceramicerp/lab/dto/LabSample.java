package com.ceramicerp.lab.dto;

import com.ceramicerp.store.DocStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One material from one truck, as the lab sees it: which GRN, from whom, how much.
 *
 * @param docNo     null while the GRN is a draft (it gets its number when posted)
 * @param arrivedAt when the GRN was made, i.e. when the truck came in
 */
public record LabSample(Long documentId, String docNo, LocalDate docDate, DocStatus status, String supplierName,
                        String vehicleNo, String weighbridgeSlipNo, String invoiceNo, Long materialId,
                        String materialCode, String materialName, String unit, BigDecimal receivedQty,
                        BigDecimal rejectedQty, Instant arrivedAt) {
}
