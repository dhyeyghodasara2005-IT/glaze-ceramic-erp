package com.ceramicerp.production.dto;

import java.math.BigDecimal;

/**
 * A lot's tiles stage by stage - the "funnel":
 *
 *   pressed 2,000 -> 50 rejected -> 1,950 good
 *                    waiting 50  -> glaze in 1,900 -> 40 rejected -> 1,860 out
 *                    waiting 60  -> kiln in 1,800  -> 36 rejected -> 1,764 fired
 *
 * @param waitingGlaze good from the press not yet entered at the glaze line (negative = more entered than pressed)
 * @param waitingKiln  out of the glaze line not yet entered at the kiln
 * @param yieldPct     fired / pressed x 100
 */
public record LotFlow(
        long pressed,
        long pressRejects,
        long pressGood,
        long glazeIn,
        long glazeRejects,
        long glazeOut,
        long kilnIn,
        long kilnRejects,
        long kilnOut,
        long waitingGlaze,
        long waitingKiln,
        BigDecimal firedSqm,
        BigDecimal yieldPct
) {
}
