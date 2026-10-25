package com.ceramicerp.maintenance.dto;

import java.math.BigDecimal;

/** How much of a material one store godown holds. */
public record SpareStock(Long materialId, Long locationId, BigDecimal quantity) {
}
