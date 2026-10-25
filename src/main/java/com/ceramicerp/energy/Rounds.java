package com.ceramicerp.energy;

import com.ceramicerp.master.Shift;
import com.ceramicerp.master.ShiftRepository;
import com.ceramicerp.master.ShiftService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Which reading round it is. Meters are read after a shift ends, so the round to fill in now is the shift that ended
 * last. Once-a-day meters are read the next morning, when the day's last shift ends: they go with that round.
 */
@Component
@RequiredArgsConstructor
class Rounds {

    static final ZoneId PLANT_ZONE = ZoneId.of("Asia/Kolkata");

    private final ShiftService shiftService;
    private final ShiftRepository shiftRepository;

    /** A round: a production date and one of its shifts (null = the once-a-day meters only). */
    record Slot(LocalDate date, Shift shift) {

        /** Has this round ended by the time of {@code last} (the last finished round)? */
        boolean endedBy(Slot last) {
            return date.isBefore(last.date()) || date.isEqual(last.date()) && shift != null && last.shift() != null
                    && shift.getSortOrder() <= last.shift().getSortOrder();
        }
    }

    List<Shift> activeShifts() {
        return shiftRepository.findByActiveTrueOrderBySortOrderAscCodeAsc();
    }

    /** The shift that ended last: during the Day shift of 25 Oct it is the Night shift of 24 Oct. */
    Slot lastFinished() {
        List<Shift> shifts = activeShifts();
        LocalDateTime now = LocalDateTime.now(PLANT_ZONE);
        if (shifts.isEmpty()) {
            return new Slot(now.toLocalDate(), null);
        }
        Optional<ShiftService.CurrentShift> current = shiftService.current(now);
        if (current.isEmpty()) {
            // a gap in the shift timings: count the day before as finished
            return new Slot(now.toLocalDate().minusDays(1), shifts.get(shifts.size() - 1));
        }
        int i = shifts.indexOf(current.get().shift());
        return i > 0 ? new Slot(current.get().prodDate(), shifts.get(i - 1))
                : new Slot(current.get().prodDate().minusDays(1), shifts.get(shifts.size() - 1));
    }

    /** The production date running now (the night shift after midnight still belongs to yesterday). */
    LocalDate today() {
        LocalDateTime now = LocalDateTime.now(PLANT_ZONE);
        return shiftService.current(now).map(ShiftService.CurrentShift::prodDate).orElse(now.toLocalDate());
    }

    /** Once-a-day meters are read with the day's last shift (or in a round of the once-a-day meters only). */
    boolean takesDailyMeters(Shift shift) {
        if (shift == null) {
            return true;
        }
        List<Shift> shifts = activeShifts();
        return !shifts.isEmpty() && shifts.get(shifts.size() - 1).getId().equals(shift.getId());
    }
}
