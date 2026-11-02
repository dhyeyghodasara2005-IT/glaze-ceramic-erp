package com.ceramicerp.labour;

import java.math.BigDecimal;

/** A worker's day, and how much of a man-day it counts as. */
public enum AttendanceStatus {
    PRESENT(BigDecimal.ONE),
    HALF(new BigDecimal("0.5")),
    ABSENT(BigDecimal.ZERO),
    /** Away with leave: not a working day (whether it is paid depends on the contractor). */
    LEAVE(BigDecimal.ZERO);

    private final BigDecimal days;

    AttendanceStatus(BigDecimal days) {
        this.days = days;
    }

    /** 1, 0.5 or 0 man-days. */
    public BigDecimal getDays() {
        return days;
    }
}
