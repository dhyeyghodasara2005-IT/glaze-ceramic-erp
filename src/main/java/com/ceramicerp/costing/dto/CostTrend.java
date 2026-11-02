package com.ceramicerp.costing.dto;

import java.math.BigDecimal;

/** One month in the trend. @param perSqm null when nothing was packed */
public record CostTrend(String month, BigDecimal total, BigDecimal sqmPacked, BigDecimal perSqm) {
}
