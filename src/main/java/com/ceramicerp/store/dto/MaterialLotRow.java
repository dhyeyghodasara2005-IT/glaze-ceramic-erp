package com.ceramicerp.store.dto;

import com.ceramicerp.store.MaterialLot;
import com.ceramicerp.store.StoreDocType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One lot in the list.
 *
 * @param documentId empty for old stock: what was in the godown when lots began
 * @param moisturePct what the GRN or the lab found
 */
public record MaterialLotRow(
        Long id,
        String lotNo,
        Long materialId,
        String materialCode,
        String materialName,
        String unitCode,
        int unitDecimals,
        String locationCode,
        String locationName,
        LocalDate receivedOn,
        String supplierName,
        String vehicleNo,
        BigDecimal moisturePct,
        BigDecimal quantityIn,
        BigDecimal quantityLeft,
        BigDecimal rate,
        Long documentId,
        StoreDocType docType,
        boolean cancelled
) {
    public static MaterialLotRow from(MaterialLot l) {
        var doc = l.getDocument();
        var unit = l.getMaterial().getUnit();
        return new MaterialLotRow(l.getId(), l.getLotNo(), l.getMaterial().getId(), l.getMaterial().getCode(),
                l.getMaterial().getName(), unit.getCode(), unit.getDecimals(), l.getLocation().getCode(),
                l.getLocation().getName(), l.getReceivedOn(), l.getSupplier() == null ? null : l.getSupplier().getName(),
                doc == null ? null : doc.getVehicleNo(), l.getLine() == null ? null : l.getLine().getMoisturePct(),
                l.getQuantityIn(), l.getQuantityLeft(), l.getRate(), doc == null ? null : doc.getId(),
                doc == null ? null : doc.getDocType(), l.isCancelled());
    }
}
