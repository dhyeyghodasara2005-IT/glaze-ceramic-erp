package com.ceramicerp.production.dto;

import java.math.BigDecimal;

/** A design for the "start a design" dropdown. */
public record DesignOption(Long id, String code, String name, String sizeCode, String finishName, BigDecimal sqmPerTile) {
}
