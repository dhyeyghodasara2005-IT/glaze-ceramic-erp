package com.ceramicerp.labour.dto;

import com.ceramicerp.labour.AttendanceSource;
import com.ceramicerp.labour.AttendanceStatus;
import com.ceramicerp.labour.Department;
import com.ceramicerp.labour.PunchMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * A day's attendance sheet, as the supervisor fills it: every active worker (of one department, or all), with what
 * was marked. No rates: a supervisor signs for days, not money.
 *
 * @param department null = every department
 * @param shifts     the shifts in use, to say which one a batch of marks was for
 * @param shiftNow   the shift running now, when the sheet is today's
 */
public record AttendanceSheet(LocalDate date, Department department, List<ShiftOption> shifts, Long shiftNow,
                              List<Row> rows, Counts counts) {

    public record ShiftOption(Long id, String code, String name) {
    }

    public record Row(Long workerId, String code, String name, Department department, String job, PunchMethod punch,
                      String contractorName, Mark mark) {
    }

    /**
     * @param by who marked it (the supervisor's signature)
     */
    public record Mark(Long id, AttendanceStatus status, BigDecimal otHours, Long shiftId, String shiftCode,
                       LocalTime inTime, LocalTime outTime, AttendanceSource source, String note, String by, Instant at) {
    }

    public record Counts(int workers, int present, int half, int absent, int leave, int notMarked) {
    }
}
