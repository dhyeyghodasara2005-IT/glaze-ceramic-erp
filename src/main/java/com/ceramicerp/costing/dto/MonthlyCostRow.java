package com.ceramicerp.costing.dto;

import com.ceramicerp.costing.MonthlyCostHead;

import java.math.BigDecimal;

/**
 * One cost head of a month.
 *
 * @param amount    null = nothing entered
 * @param lastMonth the month before's amount, to copy when it stays the same
 */
public record MonthlyCostRow(MonthlyCostHead head, BigDecimal amount, String note, String by, BigDecimal lastMonth) {
}
