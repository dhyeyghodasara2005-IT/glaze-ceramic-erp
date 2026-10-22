package com.ceramicerp.production.dto;

import java.math.BigDecimal;

/**
 * Posted press entries of one lot, added up by the database (JPQL "select new ... group by").
 * SQL SUM returns NULL for no rows and Long for whole numbers; the compact constructor tidies that.
 */
public record LotTotals(Long lotId, long entries, long pressed, long pressRejects, long dryerRejects,
                        BigDecimal powderTon, BigDecimal untracedTon) {

    public LotTotals {
        powderTon = powderTon == null ? BigDecimal.ZERO : powderTon;
        untracedTon = untracedTon == null ? BigDecimal.ZERO : untracedTon;
    }

    public static LotTotals empty(Long lotId) {
        return new LotTotals(lotId, 0, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public long good() {
        return pressed - pressRejects - dryerRejects;
    }
}
