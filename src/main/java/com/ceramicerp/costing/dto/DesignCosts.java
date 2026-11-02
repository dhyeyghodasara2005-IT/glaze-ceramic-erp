package com.ceramicerp.costing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A month's costs shared out per design. An estimate: each cost is divided by what shows who used it, so the designs
 * add up to the plant's total, but no one weighed the glaze per design.
 *
 * @param total        the plant's total for the month (the same as the cost sheet's)
 * @param unshared     cost nothing could share out, because nothing was packed or pressed
 * @param perSqm       the plant's cost per m², to compare a design with
 * @param sharedByArea material groups with no usage recorded in the month, shared by m² instead
 * @param glazeEntriesWithoutUsage glaze-line entries with no grams or no ink typed: those tiles count for less
 */
public record DesignCosts(String month, LocalDate from, LocalDate to, boolean running, BigDecimal total, BigDecimal unshared,
                          BigDecimal perSqm, List<Row> designs, List<String> sharedByArea, int glazeEntriesWithoutUsage) {

    /**
     * Every amount is this design's share of that cost.
     *
     * @param otherMaterial chemicals, spares and any other material group
     * @param perSqm        null when nothing of it was packed this month (pressed, still on the line)
     */
    public record Row(Long designId, String code, String name, BigDecimal sqm, long boxes, BigDecimal body, BigDecimal glaze,
                      BigDecimal ink, BigDecimal packing, BigDecimal otherMaterial, BigDecimal energy, BigDecimal labour,
                      BigDecimal monthly, BigDecimal total, BigDecimal perSqm, BigDecimal perBox) {
    }
}
