package com.ceramicerp.production.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The glaze & kiln home screen: each printer and the design it runs, every lot that is running (or closed in the
 * last days, its tiles may still be in the line) with its funnel, and today's figures.
 */
public record LineOverview(
        LocalDate prodDate,
        Long shiftId,
        String shiftCode,
        String shiftName,
        List<Printer> printers,
        List<LotLine> lots,
        Day today
) {
    /** One digital printer and the lot it is running (null when idle). */
    public record Printer(Long printerId, String printerCode, String printerName, LotSummary lot) {
    }

    public record LotLine(LotSummary lot, LotFlow flow) {
    }

    /**
     * @param pressGood good tiles out of the presses and dryers today: pressGood - glazeIn is what is still on the
     *                  line or in the compensators (negative = more glazed than pressed, e.g. from yesterday's stock)
     * @param gasPerSqm kiln gas / square metres fired today
     */
    public record Day(long pressGood, long glazeIn, long glazeRejects, long kilnIn, long kilnRejects, BigDecimal firedSqm,
                      BigDecimal gasScm, BigDecimal gasPerSqm) {
    }
}
