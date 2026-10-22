package com.ceramicerp.master;

import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.master.dto.ShiftRequest;
import com.ceramicerp.master.dto.ShiftResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/** Shifts master, plus "which shift is running right now, and which production date is it?". */
@Service
@RequiredArgsConstructor
public class ShiftService {

    private final ShiftRepository shiftRepository;

    /** The shift running at a moment, and the production date its work belongs to. */
    public record CurrentShift(Shift shift, LocalDate prodDate) {
    }

    @Transactional(readOnly = true)
    public List<ShiftResponse> list() {
        return shiftRepository.findAllByOrderBySortOrderAscCodeAsc().stream().map(ShiftResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ShiftResponse> listActive() {
        return shiftRepository.findByActiveTrueOrderBySortOrderAscCodeAsc().stream().map(ShiftResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ShiftResponse get(Long id) {
        return ShiftResponse.from(find(id));
    }

    @Transactional
    public ShiftResponse create(ShiftRequest req) {
        String code = Text.upperOrNull(req.code());
        if (shiftRepository.existsByCodeIgnoreCase(code)) {
            throw new BusinessException("Shift code already exists: " + code);
        }
        Shift shift = new Shift();
        apply(shift, req, code);
        return ShiftResponse.from(shiftRepository.save(shift));
    }

    @Transactional
    public ShiftResponse update(Long id, ShiftRequest req) {
        Shift shift = find(id);
        String code = Text.upperOrNull(req.code());
        if (shiftRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new BusinessException("Shift code already exists: " + code);
        }
        apply(shift, req, code);
        return ShiftResponse.from(shift);
    }

    /** Used by production to check the chosen shift. */
    public Shift find(Long id) {
        return shiftRepository.findById(id).orElseThrow(() -> new NotFoundException("Shift", id));
    }

    @Transactional(readOnly = true)
    public Optional<CurrentShift> current(LocalDateTime now) {
        return findCurrent(shiftRepository.findByActiveTrueOrderBySortOrderAscCodeAsc(), now);
    }

    /**
     * Pure logic, no database - so it is easy to test.
     * Day 08:00-20:00 at 14:00 on 23 Oct  -> (Day, 23 Oct)
     * Night 20:00-08:00 at 22:00 on 22 Oct -> (Night, 22 Oct)
     * Night 20:00-08:00 at 02:00 on 23 Oct -> (Night, 22 Oct)   the night started yesterday
     */
    static Optional<CurrentShift> findCurrent(List<Shift> shifts, LocalDateTime now) {
        LocalTime time = now.toLocalTime();
        LocalDate today = now.toLocalDate();
        for (Shift s : shifts) {
            if (!s.crossesMidnight()) {
                if (!time.isBefore(s.getStartTime()) && time.isBefore(s.getEndTime())) {
                    return Optional.of(new CurrentShift(s, today));
                }
            } else if (!time.isBefore(s.getStartTime())) {
                return Optional.of(new CurrentShift(s, today));               // evening part
            } else if (time.isBefore(s.getEndTime())) {
                return Optional.of(new CurrentShift(s, today.minusDays(1)));  // after midnight
            }
        }
        return Optional.empty();
    }

    private void apply(Shift s, ShiftRequest req, String code) {
        s.setCode(code);
        s.setName(req.name().trim());
        s.setStartTime(req.startTime());
        s.setEndTime(req.endTime());
        s.setSortOrder(req.sortOrder());
        if (req.active() != null) {
            s.setActive(req.active());
        }
    }
}
