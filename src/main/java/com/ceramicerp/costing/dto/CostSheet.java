package com.ceramicerp.costing.dto;

import com.ceramicerp.energy.MeterUnit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What a month cost, and what that comes to per m² and per box packed.
 *
 * @param to          the month's last day, or today while it runs
 * @param running     the month isn't over: its monthly costs count in full against part of its production
 * @param perSqm      null when nothing was packed
 * @param fuelInStore fuel issued from the store: shown, not added, because the meters count the fuel used
 */
public record CostSheet(String month, LocalDate from, LocalDate to, boolean running, BigDecimal sqmPacked, long boxesPacked,
                        BigDecimal total, BigDecimal perSqm, BigDecimal perBox, List<Line> lines, BigDecimal fuelInStore) {

    public enum Section { MATERIAL, ENERGY, LABOUR, MONTHLY }

    /**
     * @param code             the material group, fuel, CONTRACT / OWN_ROLLS, or the monthly cost head
     * @param name             the material group's name (the other codes are named on the screen)
     * @param quantity         energy used, or man-days
     * @param unit             what energy is counted in
     * @param rate             the average rate paid for the priced part (energy)
     * @param unpricedQuantity energy used on days without a rate: not in the amount
     * @param unpricedWorkers  workers without a rate: not in the amount
     * @param missingReadings  meter readings that should be there and aren't: the amount is low
     * @param fromSections     the main meter wasn't read, so the section meters stand in: the amount may be low
     */
    public record Line(Section section, String code, String name, BigDecimal quantity, MeterUnit unit, BigDecimal rate,
                       BigDecimal amount, BigDecimal perSqm, BigDecimal sharePct, BigDecimal unpricedQuantity,
                       Integer unpricedWorkers, Integer missingReadings, Boolean fromSections) {
    }
}
