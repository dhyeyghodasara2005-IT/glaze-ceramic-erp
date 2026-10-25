package com.ceramicerp.maintenance.dto;

import java.math.BigDecimal;

/** A spare that was used, with its value at store average cost (from the store issue). */
public record SpareUsed(int lineNo, Long materialId, String materialCode, String materialName, String unit,
                        Long locationId, String locationCode, BigDecimal quantity, BigDecimal value) {
}
