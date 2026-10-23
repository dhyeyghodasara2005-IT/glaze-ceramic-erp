package com.ceramicerp.production.dto;

import java.math.BigDecimal;

/**
 * A lot's tiles stage by stage - the "funnel":
 *
 *   pressed 2,000 -> 50 rejected -> 1,950 good
 *                    waiting 50  -> glaze in 1,900  -> 40 rejected -> 1,860 out
 *                    waiting 60  -> kiln in 1,800   -> 36 rejected -> 1,764 fired
 *       (in cooling) waiting 64  -> polish in 1,700 -> 20 broken   -> 1,680 polished
 *                    waiting 80  -> sorted 1,600    -> 16 broken   -> 1,584 packed = 792 boxes
 *
 * @param sorted         tiles that went into sorting (packed + broken)
 * @param packed         pieces put in boxes
 * @param waitingGlaze   good from the press not yet entered at the glaze line (negative = more entered than pressed)
 * @param waitingKiln    out of the glaze line not yet entered at the kiln
 * @param waitingPolish  fired, not yet polished: the tiles in the cooling area
 * @param waitingSort    polished, not yet sorted
 * @param yieldPct       fired / pressed x 100
 * @param packedYieldPct packed / pressed x 100
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
        long polishIn,
        long polishRejects,
        long polishOut,
        long sorted,
        long broken,
        long packed,
        long boxes,
        long waitingGlaze,
        long waitingKiln,
        long waitingPolish,
        long waitingSort,
        BigDecimal firedSqm,
        BigDecimal packedSqm,
        BigDecimal yieldPct,
        BigDecimal packedYieldPct
) {
}
