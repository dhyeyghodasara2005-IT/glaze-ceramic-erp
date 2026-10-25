package com.ceramicerp.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The owner's dashboard for one plant and period. Each section is there only for a user who may see that module
 * (production, finished goods, sales, store); the others are null.
 *
 * @param days one row per day of the period, for the trend chart
 */
public record DashboardResponse(
        LocalDate from,
        LocalDate to,
        Production production,
        FinishedGoods finishedGoods,
        Dispatch dispatch,
        Store store,
        Maintenance maintenance,
        List<Day> days,
        List<Attention> attention
) {
    /**
     * @param sqmPackedBefore m² packed in the same number of days just before the period (for the change)
     * @param topGradePct     share of the best sellable grade (Premium) in the boxes packed
     * @param stages          pieces at each stage in the period (each stage's own entries, so work in progress
     *                        between stages shows as a difference)
     * @param yieldPct        pieces packed / pieces pressed
     * @param gasPerSqm       kiln gas (SCM) per m² fired
     * @param powerPerSqm     metered power (kWh) per m² packed
     */
    public record Production(int boxesPacked, BigDecimal sqmPacked, BigDecimal sqmPackedBefore, String topGradeName,
                             BigDecimal topGradePct, List<GradeShare> grades, List<Stage> stages, BigDecimal yieldPct,
                             BigDecimal gasScm, BigDecimal sqmFired, BigDecimal gasPerSqm, BigDecimal powerKwh,
                             BigDecimal powerPerSqm) {
    }

    public record GradeShare(String code, String name, int sortOrder, int boxes, BigDecimal sqm, BigDecimal pct) {
    }

    /** @param recorded false when the stage has no entries in the period (e.g. no polishing line) */
    public record Stage(String key, long pieces, boolean recorded) {
    }

    /** Finished goods now (not for the period). */
    public record FinishedGoods(int boxes, BigDecimal sqm, int keptBoxes, List<GradeShare> grades,
                                List<DesignStock> topDesigns, int palletsUsed, Integer palletsCapacity,
                                int pillarsUsed, int pillarsTotal) {
    }

    public record DesignStock(String code, String name, int boxes, BigDecimal sqm) {
    }

    /**
     * @param boxesNotPlanned boxes confirmed orders still need that no free stock covers
     */
    public record Dispatch(int ordersDispatched, int boxesDispatched, BigDecimal sqmDispatched, int ordersWaiting,
                           int boxesWaiting, int boxesNotPlanned) {
    }

    /** @param lowMaterials materials at or below their reorder level */
    public record Store(BigDecimal stockValue, int lowMaterials) {
    }

    /**
     * Machine downtime in the period (step 8a) and preventive jobs today (step 8b).
     *
     * @param availabilityPct running time of all listed machines / the time they could have run
     * @param pmOnTimePct     preventive jobs of the last 30 days done by their due date
     */
    public record Maintenance(int breakdowns, long downtimeMinutes, BigDecimal availabilityPct, int machinesDown,
                              int pmOverdue, int pmDueWeek, BigDecimal pmOnTimePct) {
    }

    /** null where the user may not see that module. */
    public record Day(LocalDate date, BigDecimal packedSqm, BigDecimal dispatchedSqm) {
    }

    /**
     * Something the owner should look at. The screen writes the sentence from the kind and the numbers.
     *
     * @param what  the product, pillars or materials concerned
     * @param count how many orders / pillars / materials
     * @param boxes boxes concerned (short for orders)
     */
    public record Attention(Kind kind, String what, Integer count, Integer boxes) {

        public enum Kind {
            /** Machines reported down right now (what = their names, count = how many, boxes = longest minutes). */
            MACHINES_DOWN,
            /** Preventive jobs past their due date (what = the oldest, count = how many, boxes = its days overdue). */
            PM_OVERDUE,
            /** Confirmed orders need boxes that aren't in stock: pack more. */
            SHORT_FOR_ORDERS,
            /** Stock rows hold fewer boxes than the confirmed orders keep from them (broken or moved): plan again. */
            PLAN_TOO_FEW,
            /** Pillars at or over their capacity. */
            PILLARS_FULL,
            /** Raw materials at or below their reorder level. */
            LOW_MATERIALS
        }
    }
}
