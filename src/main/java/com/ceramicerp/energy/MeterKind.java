package com.ceramicerp.energy;

import java.util.List;

/**
 * What a meter is written down for, and how. A counter shows a running total (use = this reading - the one before);
 * an amount is the quantity used, typed as it is (coal and LPG are weighed, diesel is measured). Counters and coal
 * are written down every round; diesel, propane and LNG only when some was used (the DG set runs in power cuts).
 */
public enum MeterKind {
    GAS(true, true, MeterUnit.SCM, MeterUnit.M3),
    POWER(true, true, MeterUnit.KWH),
    /** The DG set's hour meter. */
    RUN_HOURS(true, true, MeterUnit.HOUR),
    COAL(false, true, MeterUnit.KG),
    PROPANE(false, false, MeterUnit.KG),
    LNG(false, false, MeterUnit.KG),
    DIESEL(false, false, MeterUnit.LITRE);

    private final boolean counter;
    private final boolean everyRound;
    private final List<MeterUnit> units;

    MeterKind(boolean counter, boolean everyRound, MeterUnit... units) {
        this.counter = counter;
        this.everyRound = everyRound;
        this.units = List.of(units);
    }

    /** true: read from a running total; false: the amount used is typed. */
    public boolean isCounter() {
        return counter;
    }

    /** true: written down every round, so a round without it is missing it; false: only when some was used. */
    public boolean isEveryRound() {
        return everyRound;
    }

    /** The units it may be counted in; the first is the usual one. */
    public List<MeterUnit> getUnits() {
        return units;
    }
}
