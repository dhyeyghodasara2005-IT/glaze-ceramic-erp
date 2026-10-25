package com.ceramicerp.maintenance;

import java.time.LocalDate;

/** Where a preventive job stands against its due date. */
public enum DueStatus {
    OVERDUE,
    TODAY,
    /** Within the next 7 days. */
    WEEK,
    LATER;

    static final int WEEK_DAYS = 7;

    static DueStatus of(LocalDate due, LocalDate today) {
        if (due.isBefore(today)) {
            return OVERDUE;
        }
        if (due.isEqual(today)) {
            return TODAY;
        }
        return due.isAfter(today.plusDays(WEEK_DAYS)) ? LATER : WEEK;
    }
}
