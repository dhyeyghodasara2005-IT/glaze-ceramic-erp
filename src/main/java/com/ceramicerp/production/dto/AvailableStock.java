package com.ceramicerp.production.dto;

import java.math.BigDecimal;

/** How much of a material lies in a godown - quantity only (supervisors don't need rupee values). */
public record AvailableStock(Long materialId, Long locationId, BigDecimal quantity) {
}
