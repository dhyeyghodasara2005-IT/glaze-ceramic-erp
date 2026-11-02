package com.ceramicerp.production.dto;

import com.ceramicerp.production.LotStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** The trace between the trucks of raw material and the tiles, in both directions. */
public final class MaterialTrace {

    private MaterialTrace() {
    }

    /**
     * A truck behind a mill charge or a design lot.
     *
     * @param quantity how much of it went into those mill charges (a design lot may have used only part of their powder)
     * @param batches  the mill charges that took from it
     */
    public record Truck(Long lotId, String lotNo, String materialCode, String materialName, String unitCode,
                        int unitDecimals, String supplierName, String vehicleNo, LocalDate receivedOn,
                        BigDecimal moisturePct, boolean oldStock, BigDecimal quantity, List<String> batches) {
    }

    /** Where a truck went: the mill charges that took from it and the design lots pressed from their powder. */
    public record Reach(List<BatchUse> batches, List<DesignLotUse> designLots) {
    }

    public record BatchUse(Long batchId, String batchNo, LocalDate prodDate, BigDecimal quantity) {
    }

    public record DesignLotUse(Long lotId, String lotNo, String designCode, String designName, LotStatus status) {
    }
}
