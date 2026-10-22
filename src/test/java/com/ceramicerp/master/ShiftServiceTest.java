package com.ceramicerp.master;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A plain unit test: no Spring, no database - ShiftService.findCurrent is pure logic,
 * so we can feed it shifts and times directly. Runs in milliseconds.
 */
class ShiftServiceTest {

    private static Shift shift(String code, String start, String end) {
        Shift s = new Shift();
        s.setCode(code);
        s.setName(code);
        s.setStartTime(LocalTime.parse(start));
        s.setEndTime(LocalTime.parse(end));
        return s;
    }

    private final List<Shift> twoShifts = List.of(shift("A", "08:00", "20:00"), shift("B", "20:00", "08:00"));

    private ShiftService.CurrentShift at(String dateTime) {
        return ShiftService.findCurrent(twoShifts, LocalDateTime.parse(dateTime)).orElseThrow();
    }

    @Test
    void dayShiftBelongsToToday() {
        var c = at("2026-10-23T14:00");
        assertThat(c.shift().getCode()).isEqualTo("A");
        assertThat(c.prodDate()).isEqualTo(LocalDate.of(2026, 10, 23));
    }

    @Test
    void nightShiftAfterMidnightBelongsToYesterday() {
        var evening = at("2026-10-22T22:00");
        assertThat(evening.shift().getCode()).isEqualTo("B");
        assertThat(evening.prodDate()).isEqualTo(LocalDate.of(2026, 10, 22));

        var lateNight = at("2026-10-23T02:00");
        assertThat(lateNight.shift().getCode()).isEqualTo("B");
        assertThat(lateNight.prodDate()).isEqualTo(LocalDate.of(2026, 10, 22));
    }

    @Test
    void boundariesBelongToTheShiftThatStarts() {
        assertThat(at("2026-10-23T08:00").shift().getCode()).isEqualTo("A");
        assertThat(at("2026-10-23T20:00").shift().getCode()).isEqualTo("B");
        assertThat(at("2026-10-23T07:59").shift().getCode()).isEqualTo("B");
    }

    @Test
    void gapsBetweenShiftsFindNothing() {
        List<Shift> withGap = List.of(shift("A", "08:00", "16:00"));
        assertThat(ShiftService.findCurrent(withGap, LocalDateTime.parse("2026-10-23T17:00"))).isEmpty();
    }
}
