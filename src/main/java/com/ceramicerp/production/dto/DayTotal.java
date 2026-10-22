package com.ceramicerp.production.dto;

import java.math.BigDecimal;

/** How many entries in a day, and their total tonnes. Built directly by a JPQL "select new" query. */
public record DayTotal(long count, BigDecimal tonnes) {

    /** A compact constructor runs before the fields are set: SQL SUM of no rows is NULL, we want 0. */
    public DayTotal {
        tonnes = tonnes == null ? BigDecimal.ZERO : tonnes;
    }
}
