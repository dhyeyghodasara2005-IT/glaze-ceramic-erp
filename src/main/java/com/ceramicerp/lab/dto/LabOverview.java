package com.ceramicerp.lab.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The lab at a glance: samples waiting now, and the last 30 days.
 *
 * @param rejectedPct null when nothing was tested
 */
public record LabOverview(int waiting, int tested, int rejected, BigDecimal rejectedPct, List<SupplierQuality> suppliers) {

    /** @param supplierId null for GRNs without a supplier */
    public record SupplierQuality(Long supplierId, String name, int tested, int rejected, BigDecimal rejectedPct) {
    }
}
