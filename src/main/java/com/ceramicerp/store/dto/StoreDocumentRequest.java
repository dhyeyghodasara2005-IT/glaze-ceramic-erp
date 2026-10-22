package com.ceramicerp.store.dto;

import com.ceramicerp.store.AdjustmentReason;
import com.ceramicerp.store.StoreDocType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Create / edit a store document (always saved as DRAFT; posting is a separate call).
 * Fields for other document types are simply left empty.
 */
public record StoreDocumentRequest(
        @NotNull(message = "Select a plant")
        Long plantId,

        @NotNull
        StoreDocType docType,

        @NotNull(message = "Enter the date")
        LocalDate docDate,

        // GRN
        Long supplierId,
        @Size(max = 40) String supplierInvoiceNo,
        LocalDate supplierInvoiceDate,
        @Size(max = 20) String vehicleNo,
        @Size(max = 30) String weighbridgeSlipNo,

        // ISSUE
        Long machineId,
        @Size(max = 100) String issuedTo,

        // ADJUSTMENT
        AdjustmentReason reason,

        @Size(max = 500)
        String remarks,

        /** @Valid = also check every line's own annotations. */
        @NotEmpty(message = "Add at least one line") @Size(max = 200) @Valid
        List<Line> lines
) {
    public record Line(
            @NotNull(message = "Select a material")
            Long materialId,

            @NotNull(message = "Select a godown")
            Long locationId,

            /** ISSUE / ADJUSTMENT quantity (GRN ignores it: accepted = received - rejected). */
            @Digits(integer = 13, fraction = 3)
            BigDecimal quantity,

            @DecimalMin("0") @Digits(integer = 13, fraction = 3)
            BigDecimal receivedQty,

            @DecimalMin("0") @Digits(integer = 13, fraction = 3)
            BigDecimal rejectedQty,

            @DecimalMin("0") @Digits(integer = 10, fraction = 4)
            BigDecimal rate,

            @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
            BigDecimal moisturePct,

            @Size(max = 255)
            String remarks
    ) {
    }
}
