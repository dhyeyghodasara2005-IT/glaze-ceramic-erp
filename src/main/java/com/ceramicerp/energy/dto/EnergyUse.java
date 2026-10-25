package com.ceramicerp.energy.dto;

import com.ceramicerp.energy.MeterUnit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Energy used on the production days of a period, and what it means per m² and per tonne.
 *
 * @param powerParts power by section meter, plus what the main meter counted beyond them (meterId null)
 * @param lastRound  the last finished shift and yesterday, and the meters not read for them yet
 */
public record EnergyUse(LocalDate from, LocalDate to, Totals totals, List<Day> days, List<MeterUse> meters,
                        List<Part> powerParts, LastRound lastRound) {

    /**
     * Each figure is null when nothing was written down for it.
     *
     * @param kilnGas    gas of the meters on kilns
     * @param sqmFired   m² out of the kilns that have a gas meter
     * @param gasPerSqm  main gas per m² packed
     * @param coalPerTon kg of coal per tonne of spray-dried powder
     */
    public record Totals(BigDecimal gasMain, MeterUnit gasMainUnit, BigDecimal kilnGas, MeterUnit kilnGasUnit,
                         BigDecimal sqmFired, BigDecimal kilnGasPerSqm, BigDecimal powerMain, BigDecimal sqmPacked,
                         BigDecimal powerPerSqm, BigDecimal gasPerSqm, BigDecimal coalKg, BigDecimal powderTon,
                         BigDecimal coalPerTon, BigDecimal propaneKg, BigDecimal lngKg, BigDecimal dgHours,
                         BigDecimal dieselLitres, BigDecimal dieselPerHour) {
    }

    public record Day(LocalDate date, BigDecimal gasMain, BigDecimal kilnGas, BigDecimal powerMain) {
    }

    /**
     * @param readings how many readings it has for the period's rounds
     * @param expected how many it should have (rounds that have ended)
     */
    public record MeterUse(MeterResponse meter, BigDecimal used, BigDecimal perDay, int readings, int expected,
                           Last last) {
    }

    public record Last(BigDecimal reading, BigDecimal used, LocalDate readOn, String shiftCode) {
    }

    public record Part(Long meterId, String name, BigDecimal kwh) {
    }

    /**
     * @param missing    shift meters not read for the shift that ended last
     * @param dayMissing once-a-day meters not read for {@code day} (yesterday: they were due this morning)
     */
    public record LastRound(LocalDate date, Long shiftId, String shiftCode, List<String> missing, LocalDate day,
                            List<String> dayMissing) {
    }
}
