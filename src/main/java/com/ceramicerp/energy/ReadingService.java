package com.ceramicerp.energy;

import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.energy.dto.MeterResponse;
import com.ceramicerp.energy.dto.RoundRequest;
import com.ceramicerp.energy.dto.RoundResponse;
import com.ceramicerp.master.Shift;
import com.ceramicerp.master.ShiftService;
import com.ceramicerp.organization.Plant;
import com.ceramicerp.organization.PlantService;
import com.ceramicerp.security.PlantAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reading rounds: after each shift (and once a day for the main meters) the readings are typed in one go. For a
 * counter, the use is this reading minus the one before; a reading typed later between two others (or corrected)
 * also changes the use of the one after it.
 */
@Service
@RequiredArgsConstructor
public class ReadingService {

    private static final Pageable ONE = PageRequest.of(0, 1);
    /** How many past uses decide what is "usual" for a meter. */
    private static final int USUAL_OF = 10;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private final MeterRepository meterRepository;
    private final MeterReadingRepository readingRepository;
    private final PlantService plantService;
    private final ShiftService shiftService;
    private final Rounds rounds;

    /**
     * A round as the screen shows it.
     *
     * @param date    null = the round to read now (the shift that ended last)
     * @param shiftId null with a date = the once-a-day meters only
     */
    @Transactional(readOnly = true)
    public RoundResponse round(Long plantId, LocalDate date, Long shiftId) {
        PlantAccess.require(plantId);
        Rounds.Slot slot = date == null ? rounds.lastFinished()
                : new Rounds.Slot(date, shiftId == null ? null : shiftService.find(shiftId));
        Shift shift = slot.shift();
        Map<Long, MeterReading> saved = readingRepository.inRound(plantId, slot.date(), shift == null ? null : shift.getId())
                .stream()
                .filter(r -> r.getMeter().getReadEvery() == ReadEvery.DAY ? r.getShift() == null : r.getShift() != null)
                .collect(Collectors.toMap(r -> r.getMeter().getId(), Function.identity(), (a, b) -> a));

        List<RoundResponse.Line> lines = new ArrayList<>();
        for (Meter m : meterRepository.findByPlantIdOrderBySortOrderAscCodeAsc(plantId)) {
            boolean due = m.getReadEvery() == ReadEvery.DAY ? rounds.takesDailyMeters(shift) : shift != null;
            if (!m.isActive() || !due) {
                continue;
            }
            int order = m.getReadEvery() == ReadEvery.DAY ? -1 : shift.getSortOrder();
            MeterReading before = m.getKind().isCounter()
                    ? first(readingRepository.before(m.getId(), slot.date(), order, ONE)) : null;
            MeterReading s = saved.get(m.getId());
            lines.add(new RoundResponse.Line(MeterResponse.from(m),
                    before == null ? null : new RoundResponse.Previous(before.getReading(), before.getReadOn(), shiftCode(before)),
                    usual(readingRepository.recentUses(m.getId(), PageRequest.of(0, USUAL_OF))),
                    s == null ? null : new RoundResponse.Saved(s.getId(), s.getReading(), s.getUsed(), s.isRestarted(),
                            s.getNote(), s.getUpdatedBy(), s.getUpdatedAt())));
        }
        List<RoundResponse.ShiftRef> shifts = rounds.activeShifts().stream().map(ReadingService::ref).toList();
        return new RoundResponse(slot.date(), shift == null ? null : ref(shift), shifts, lines);
    }

    /** Saves a round's readings. Lines with nothing typed are left as they are. */
    @Transactional
    public RoundResponse save(RoundRequest req) {
        Plant plant = plantService.find(req.plantId());
        PlantAccess.require(plant.getId());
        if (req.date().isAfter(rounds.today())) {
            throw new BusinessException("Readings can't be entered for a day that hasn't come");
        }
        Shift shift = req.shiftId() == null ? null : shiftService.find(req.shiftId());
        for (RoundRequest.Line line : req.lines()) {
            if (line.reading() == null && line.used() == null) {
                continue;
            }
            Meter m = meterRepository.findLockedById(line.meterId())
                    .orElseThrow(() -> new NotFoundException("Meter", line.meterId()));
            if (!m.getPlant().getId().equals(plant.getId())) {
                throw new BusinessException("Meter " + m.getCode() + " is not in plant " + plant.getCode());
            }
            if (!m.isActive()) {
                throw new BusinessException(m.getName() + " is switched off");
            }
            Shift at = m.getReadEvery() == ReadEvery.DAY ? null : shift;
            if (m.getReadEvery() == ReadEvery.SHIFT && shift == null) {
                throw new BusinessException(m.getName() + " is read after each shift: choose the shift");
            }
            MeterReading r = readingRepository.findSlot(m.getId(), req.date(), at == null ? null : at.getId())
                    .orElseGet(MeterReading::new);
            r.setPlant(plant);
            r.setMeter(m);
            r.setReadOn(req.date());
            r.setShift(at);
            r.setNote(Text.trimToNull(line.note()));
            if (m.getKind().isCounter()) {
                counted(r, m, line);
            } else {
                if (line.used() == null) {
                    throw new BusinessException(m.getName() + ": type the amount used");
                }
                r.setReading(null);
                r.setUsed(line.used());
                r.setRestarted(false);
                readingRepository.save(r);
            }
        }
        readingRepository.flush();
        return round(plant.getId(), req.date(), shift == null ? null : shift.getId());
    }

    /** Takes a reading back (typed by mistake); the reading after it then counts from the one before. */
    @Transactional
    public void delete(Long id) {
        MeterReading r = readingRepository.findById(id).orElseThrow(() -> new NotFoundException("Meter reading", id));
        PlantAccess.require(r.getPlant().getId());
        Meter m = meterRepository.findLockedById(r.getMeter().getId()).orElseThrow();
        MeterReading before = first(readingRepository.before(m.getId(), r.getReadOn(), r.order(), ONE));
        MeterReading after = first(readingRepository.after(m.getId(), r.getReadOn(), r.order(), ONE));
        readingRepository.delete(r);
        if (m.getKind().isCounter() && after != null && !after.isRestarted()) {
            after.setUsed(before == null ? BigDecimal.ZERO : after.getReading().subtract(before.getReading()).max(BigDecimal.ZERO));
        }
    }

    // ------------------------------------------------------------------ helpers

    /**
     * A counter's reading: never below the one before (unless the meter restarted), never above the one after.
     * The first reading of a meter is where it starts counting: its use is 0.
     */
    private void counted(MeterReading r, Meter m, RoundRequest.Line line) {
        if (line.reading() == null) {
            throw new BusinessException(m.getName() + ": type the number on the meter");
        }
        BigDecimal reading = line.reading();
        boolean restarted = Boolean.TRUE.equals(line.restarted());
        int order = r.order();
        MeterReading before = first(readingRepository.before(m.getId(), r.getReadOn(), order, ONE));
        MeterReading after = first(readingRepository.after(m.getId(), r.getReadOn(), order, ONE));
        if (!restarted && before != null && reading.compareTo(before.getReading()) < 0) {
            throw new BusinessException(m.getName() + ": " + plain(reading) + " is lower than the reading before ("
                    + plain(before.getReading()) + " on " + when(before)
                    + "). If the meter was changed or started again from 0, tick \"Meter restarted\"");
        }
        if (after != null && !after.isRestarted() && after.getReading().compareTo(reading) < 0) {
            throw new BusinessException(m.getName() + ": " + plain(reading) + " is higher than the reading after it ("
                    + plain(after.getReading()) + " on " + when(after) + ")");
        }
        r.setReading(reading);
        r.setRestarted(restarted);
        r.setUsed(restarted ? reading : before == null ? BigDecimal.ZERO : reading.subtract(before.getReading()));
        readingRepository.save(r);
        if (after != null && !after.isRestarted()) {
            after.setUsed(after.getReading().subtract(reading));
        }
    }

    /** The middle value of the recent uses: one odd day doesn't move it, as it would an average. */
    static BigDecimal usual(List<BigDecimal> uses) {
        List<BigDecimal> sorted = uses.stream().filter(u -> u.signum() > 0).sorted().toList();
        return sorted.size() < 3 ? null : sorted.get(sorted.size() / 2);
    }

    private static RoundResponse.ShiftRef ref(Shift s) {
        return new RoundResponse.ShiftRef(s.getId(), s.getCode(), s.getName(), s.getSortOrder());
    }

    private static String shiftCode(MeterReading r) {
        return r.getShift() == null ? null : r.getShift().getCode();
    }

    private static String when(MeterReading r) {
        return r.getReadOn().format(DATE) + (r.getShift() == null ? "" : ", shift " + r.getShift().getCode());
    }

    private static String plain(BigDecimal n) {
        return n.stripTrailingZeros().toPlainString();
    }

    private static <T> T first(List<T> list) {
        return list.isEmpty() ? null : list.get(0);
    }
}
