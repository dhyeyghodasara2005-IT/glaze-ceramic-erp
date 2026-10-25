package com.ceramicerp.maintenance.dto;

import java.math.BigDecimal;

/**
 * Where preventive maintenance stands in a plant today.
 *
 * @param dueWeek     due in the next 7 days (not counting today)
 * @param onTimePct   share of the jobs done in the last 30 days that were done by their due date
 * @param oldest      the job overdue the longest, if any
 */
public record PmOverview(int activeJobs, int overdue, int dueToday, int dueWeek, int doneLast30, int onTimeLast30,
                         BigDecimal onTimePct, PmTaskSummary oldest) {
}
