package com.ceramicerp.maintenance.dto;

import com.ceramicerp.maintenance.BreakdownCategory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Machine downtime in a period (plant time, up to now for today).
 *
 * @param periodMinutes the minutes each machine could have run in the period
 */
public record DowntimeResponse(LocalDate from, LocalDate to, long periodMinutes, int breakdowns, long downtimeMinutes,
                               List<MachineDowntime> machines, List<CategoryDowntime> categories, List<DownNow> downNow) {

    /**
     * @param breakdowns      breakdowns that started in the period
     * @param downtimeMinutes minutes the machine was down within the period
     * @param mttrMinutes     mean time to repair: the average length of its closed breakdowns
     * @param mtbfHours       mean time between failures: running hours per breakdown
     * @param availabilityPct running time / period time
     */
    public record MachineDowntime(MachineRef machine, int breakdowns, long downtimeMinutes, Integer mttrMinutes,
                                  BigDecimal mtbfHours, BigDecimal availabilityPct, boolean downNow) {
    }

    public record CategoryDowntime(BreakdownCategory category, int breakdowns, long minutes) {
    }

    public record DownNow(Long id, String breakdownNo, MachineRef machine, LocalDateTime stoppedAt, long minutes,
                          String symptom) {
    }
}
