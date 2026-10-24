package com.ceramicerp.sales.dto;

import com.ceramicerp.sales.OrderSource;
import com.ceramicerp.sales.SalesOrderStatus;
import com.ceramicerp.sales.SalesOrderType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** A sales order with the checks the office looks at before confirming. Built by SalesOrderService. */
public record SalesOrderResponse(
        Long id,
        Long plantId,
        String plantCode,
        String slipNo,
        LocalDate slipDate,
        String orderRef,
        LocalDate orderDate,
        SalesOrderType orderType,
        SalesOrderStatus status,
        OrderSource source,
        SalesParty party,
        SalesOption transporter,
        Integer slipBoxes,
        BigDecimal slipWeightKg,
        String remarks,
        List<Line> lines,
        Totals totals,
        Instant confirmedAt,
        String confirmedBy,
        Instant cancelledAt,
        String cancelledBy,
        String cancelReason,
        Instant createdAt,
        String createdBy,
        Long version
) {
    /**
     * @param inStock    boxes of this design and grade in the plant now, any batch, shade or caliber
     *                   (null while the line has no design and grade, or once the order is cancelled)
     * @param shortBy    boxes missing from the stock (0 = enough)
     * @param weightKg   boxes x box weight of the size; null when the size has no box weight
     * @param remembered true when this description is already remembered as exactly this design and grade
     */
    public record Line(Long id, int lineNo, String description, SalesOption design, SalesOption grade, int boxes,
                       BigDecimal mrpBox, BigDecimal mrpPiece, String batchRef, BigDecimal sqm, BigDecimal weightKg,
                       Long inStock, Integer shortBy, boolean remembered) {
    }

    /**
     * Glaze's own totals next to the ones printed on the slip.
     *
     * @param weightKg     null when a line has no design yet or its size has no box weight
     * @param boxesDiff    our boxes - the slip's (null when the slip total isn't entered)
     * @param weightDiffKg our weight - the slip's
     * @param weightOk     within 2% of the slip's weight (box weights in the two systems may differ a little)
     */
    public record Totals(int boxes, BigDecimal sqm, BigDecimal weightKg, Integer boxesDiff, BigDecimal weightDiffKg,
                         Boolean weightOk, int unmatchedLines, int shortLines) {
    }
}
