package com.ceramicerp.production.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * The body preparation supervisor's home screen in one call.
 *
 * @param inMills   batches CHARGED (milling now), oldest first
 * @param waiting   batches DISCHARGED (slip in tanks, waiting for the spray dryer)
 * @param charged   ball mill charges on this production date
 * @param sprayed   posted spray dryer runs on this production date (tonnes = powder)
 */
public record BodyPrepOverview(
        LocalDate prodDate,
        Long shiftId,
        String shiftCode,
        String shiftName,
        List<BodyBatchSummary> inMills,
        List<BodyBatchSummary> waiting,
        DayTotal charged,
        DayTotal sprayed
) {
}
