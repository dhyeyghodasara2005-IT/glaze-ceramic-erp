package com.ceramicerp.energy.dto;

import com.ceramicerp.energy.MeterKind;
import com.ceramicerp.energy.MeterUnit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What was used in a period, ready to be priced: nothing counted twice.
 *
 * @param fromSections kinds whose main meter has no readings, so the section meters stand in (the figure may be low)
 * @param missing      readings that should be there and aren't, per kind
 */
public record CostableUse(List<Use> uses, Set<MeterKind> fromSections, Map<MeterKind, Integer> missing) {

    /** @param day the production day it was used on: it decides which rate applies */
    public record Use(MeterKind kind, MeterUnit unit, LocalDate day, BigDecimal used) {
    }
}
