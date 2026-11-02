package com.ceramicerp.labour.dto;

import com.ceramicerp.labour.Department;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A month of labour: per contractor, man-days (present = 1, half day = 0.5), overtime, and what it comes to at each
 * worker's rates.
 *
 * @param to the month's last day, or today while the month is running
 */
public record LabourMonth(String month, LocalDate from, LocalDate to, List<ContractorMonth> contractors,
                          List<DepartmentDays> departments, BigDecimal manDays, BigDecimal otHours, BigDecimal amount) {

    /**
     * @param contractorId null for workers on the plant's own rolls
     * @param unpriced     workers who worked but have no day rate (their days aren't in the amount)
     */
    public record ContractorMonth(Long contractorId, String name, int workers, BigDecimal manDays, BigDecimal otHours,
                                  BigDecimal amount, int unpriced, List<WorkerMonth> rows) {
    }

    public record WorkerMonth(Long workerId, String code, String name, Department department, int present, int half,
                              int absent, int leave, BigDecimal manDays, BigDecimal otHours, BigDecimal dayRate,
                              BigDecimal otRate, BigDecimal amount) {
    }

    public record DepartmentDays(Department department, BigDecimal manDays) {
    }
}
