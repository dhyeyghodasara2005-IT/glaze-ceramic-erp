package com.ceramicerp.maintenance.dto;

/**
 * A material that can be used as a spare. Spares & consumables come first in the list (spare = true), but grease
 * from the chemicals group can be used too.
 */
public record SpareOption(Long id, String code, String name, String unit, int decimals, boolean spare) {
}
