package com.ceramicerp.energy.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A reading round as the screen shows it: every meter due in it, the reading before (to see the use at once),
 * what is usual for it, and what has been saved. The once-a-day meters come with the day's last shift.
 *
 * @param shift  null = a round of the once-a-day meters only
 * @param shifts the shifts to choose from
 */
public record RoundResponse(LocalDate date, ShiftRef shift, List<ShiftRef> shifts, List<Line> lines) {

    public record ShiftRef(Long id, String code, String name, int sortOrder) {
    }

    /**
     * @param previous the reading before this one (counters), null for the first
     * @param usual    the middle of its last uses (to warn about a number far from it), null with too few
     */
    public record Line(MeterResponse meter, Previous previous, BigDecimal usual, Saved saved) {
    }

    public record Previous(BigDecimal reading, LocalDate readOn, String shiftCode) {
    }

    public record Saved(Long id, BigDecimal reading, BigDecimal used, boolean restarted, String note, String by,
                        Instant at) {
    }
}
