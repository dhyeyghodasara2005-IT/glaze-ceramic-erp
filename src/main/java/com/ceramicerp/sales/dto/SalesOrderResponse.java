package com.ceramicerp.sales.dto;

import com.ceramicerp.fg.PillarKind;
import com.ceramicerp.fg.PillarZone;
import com.ceramicerp.sales.OrderSource;
import com.ceramicerp.sales.PickReason;
import com.ceramicerp.sales.SalesOrderStatus;
import com.ceramicerp.sales.SalesOrderType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** A sales order with its checks and (once confirmed) its loading plan. Built by SalesOrderView. */
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
        String truckNo,
        String driverName,
        String driverMobile,
        String lrNo,
        Integer slipBoxes,
        BigDecimal slipWeightKg,
        String remarks,
        List<Line> lines,
        List<Pick> picks,
        Totals totals,
        Instant confirmedAt,
        String confirmedBy,
        Instant plannedAt,
        String dispatchNo,
        LocalDate dispatchDate,
        Instant dispatchedAt,
        String dispatchedBy,
        Instant undoneAt,
        String undoneBy,
        String undoReason,
        Instant cancelledAt,
        String cancelledBy,
        String cancelReason,
        Instant createdAt,
        String createdBy,
        Long version
) {
    /**
     * @param inStock       boxes of this design and grade in the plant now, any batch, shade or caliber (missing while
     *                      the line has no design and grade, and once the order is dispatched or cancelled)
     * @param keptElsewhere of those, boxes kept for other confirmed orders
     * @param shortBy       boxes missing after the other orders (0 = enough)
     * @param weightKg      boxes x box weight of the size; null when the size has no box weight
     * @param planned       boxes in this line's loading plan (missing while a draft)
     * @param loaded        boxes ticked as loaded (missing until something is ticked)
     * @param remembered    true when this description is already remembered as exactly this design and grade
     */
    public record Line(Long id, int lineNo, String description, SalesOption design, SalesOption grade, int boxes,
                       BigDecimal mrpBox, BigDecimal mrpPiece, String batchRef, BigDecimal sqm, BigDecimal weightKg,
                       Long inStock, Long keptElsewhere, Integer shortBy, Integer planned, Integer loaded,
                       boolean remembered) {
    }

    /**
     * One row of the loading plan, in walk order.
     *
     * @param boxesThere     boxes in that stock row now
     * @param tooFew         fewer boxes there now than the confirmed orders keep (broken or moved since): plan again
     * @param boxesPerPallet to show "2 pallets + 12 boxes"; null when the size doesn't say
     */
    public record Pick(Long id, Long lineId, int lineNo, Long stockId, String godownCode, String pillarCode,
                       PillarZone zone, Integer walkOrder, PillarKind pillarKind, String designName, String gradeName,
                       String batchCode, String shade, String caliber, int plannedBoxes, Integer loadedBoxes,
                       PickReason reason, Integer boxesPerPallet, int boxesThere, boolean tooFew, Instant loadedAt,
                       String loadedBy) {
    }

    /**
     * Glaze's own totals next to the ones printed on the slip, and how far the loading is.
     *
     * @param weightKg     null when a line has no design yet or its size has no box weight
     * @param boxesDiff    our boxes - the slip's (null when the slip total isn't entered)
     * @param weightDiffKg our weight - the slip's
     * @param weightOk     within 2% of the slip's weight (box weights in the two systems may differ a little)
     * @param picksToTick  plan rows the loader hasn't ticked yet
     */
    public record Totals(int boxes, BigDecimal sqm, BigDecimal weightKg, Integer boxesDiff, BigDecimal weightDiffKg,
                         Boolean weightOk, int unmatchedLines, int shortLines, int plannedBoxes, int loadedBoxes,
                         int picksToTick) {
    }
}
