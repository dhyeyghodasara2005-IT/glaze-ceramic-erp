package com.ceramicerp.labour;

import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.labour.dto.AttendanceRequest;
import com.ceramicerp.labour.dto.AttendanceSheet;
import com.ceramicerp.labour.dto.LabourMonth;
import com.ceramicerp.master.Shift;
import com.ceramicerp.master.ShiftRepository;
import com.ceramicerp.master.ShiftService;
import com.ceramicerp.organization.Plant;
import com.ceramicerp.organization.PlantService;
import com.ceramicerp.party.Party;
import com.ceramicerp.security.PlantAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Attendance: each day the supervisor marks their workers from the signed cards (present, half day, absent, leave,
 * overtime hours), and the labour office adds up the month per contractor.
 */
@Service
@RequiredArgsConstructor
public class AttendanceService {

    static final ZoneId PLANT_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final AttendanceRepository attendanceRepository;
    private final WorkerRepository workerRepository;
    private final PlantService plantService;
    private final ShiftService shiftService;
    private final ShiftRepository shiftRepository;

    /** What the dashboard says about a day nobody finished marking. */
    public record Unmarked(LocalDate date, int workers, List<Department> departments) {
    }

    /**
     * A day's sheet: the workers on the rolls that day (and anyone already marked), with their marks.
     *
     * @param date       null = today's production date
     * @param department null = every department
     */
    @Transactional(readOnly = true)
    public AttendanceSheet sheet(Long plantId, LocalDate date, Department department) {
        PlantAccess.require(plantId);
        LocalDate day = date == null ? today() : date;
        Map<Long, Attendance> marks = marksOn(plantId, day);
        List<AttendanceSheet.Row> rows = workerRepository.search(plantId, null, null, department, null).stream()
                .filter(w -> marks.containsKey(w.getId()) || onRolls(w, day))
                .map(w -> row(w, marks.get(w.getId())))
                .toList();
        LocalDateTime now = LocalDateTime.now(PLANT_ZONE);
        Long shiftNow = shiftService.current(now).filter(c -> c.prodDate().equals(day)).map(c -> c.shift().getId()).orElse(null);
        List<AttendanceSheet.ShiftOption> shifts = shiftRepository.findByActiveTrueOrderBySortOrderAscCodeAsc().stream()
                .map(s -> new AttendanceSheet.ShiftOption(s.getId(), s.getCode(), s.getName()))
                .toList();
        return new AttendanceSheet(day, department, shifts, shiftNow, rows, counts(rows));
    }

    /**
     * Saves the marks of some workers for one day. Only the lines sent change: two supervisors can mark their own
     * sections of the same day without touching each other's.
     */
    @Transactional
    public AttendanceSheet save(AttendanceRequest req) {
        Plant plant = plantService.find(req.plantId());
        PlantAccess.require(plant.getId());
        LocalDate day = req.date();
        if (day.isAfter(today())) {
            throw new BusinessException("Attendance can't be marked for a day that hasn't started");
        }
        Shift shift = req.shiftId() == null ? null : shiftService.find(req.shiftId());

        Set<Long> ids = new HashSet<>();
        for (AttendanceRequest.Line line : req.lines()) {
            if (!ids.add(line.workerId())) {
                throw new BusinessException("A worker is in the list twice");
            }
        }
        Map<Long, Worker> workers = workerRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Worker::getId, Function.identity()));
        Map<Long, Attendance> marks = marksOn(plant.getId(), day);

        for (AttendanceRequest.Line line : req.lines()) {
            Worker w = workers.get(line.workerId());
            if (w == null) {
                throw new NotFoundException("Worker", line.workerId());
            }
            if (!w.getPlant().getId().equals(plant.getId())) {
                throw new BusinessException("Worker " + w.getCode() + " is not in plant " + plant.getCode());
            }
            Attendance a = marks.get(w.getId());
            if (line.status() == null) {
                // the mark taken back: the day is not marked again
                if (a != null) {
                    attendanceRepository.delete(a);
                }
                continue;
            }
            if (a == null && !onRolls(w, day)) {
                throw new BusinessException(!w.isActive() ? w.getName() + " (" + w.getCode() + ") no longer works here"
                        : w.getName() + " (" + w.getCode() + ") joined on " + DATE.format(w.getJoinedOn()));
            }
            BigDecimal ot = line.otHours() == null ? BigDecimal.ZERO : line.otHours();
            if (ot.signum() > 0 && line.status().getDays().signum() == 0) {
                throw new BusinessException("No overtime on a day " + w.getName() + " didn't work");
            }
            if (a == null) {
                a = new Attendance();
                a.setPlant(plant);
                a.setWorker(w);
                a.setWorkDate(day);
                a.setSource(AttendanceSource.CARD);
            }
            a.setStatus(line.status());
            a.setOtHours(ot);
            a.setShift(shift);
            a.setNote(Text.trimToNull(line.note()));
            attendanceRepository.save(a);
        }
        attendanceRepository.flush();
        return sheet(plant.getId(), day, null);
    }

    /** Workers on the rolls that day with nothing marked, or null when the day is fully marked. */
    @Transactional(readOnly = true)
    public Unmarked unmarked(Long plantId, LocalDate date) {
        PlantAccess.require(plantId);
        Map<Long, Attendance> marks = marksOn(plantId, date);
        List<Worker> missing = workerRepository.search(plantId, true, null, null, null).stream()
                .filter(w -> onRolls(w, date) && !marks.containsKey(w.getId()))
                .toList();
        if (missing.isEmpty()) {
            return null;
        }
        return new Unmarked(date, missing.size(), missing.stream().map(Worker::getDepartment).distinct().sorted().toList());
    }

    /** The day before today's production date: the last one that should be fully marked. */
    public LocalDate yesterday() {
        return today().minusDays(1);
    }

    /**
     * A month per contractor: man-days, overtime, and what they come to at each worker's rates (today's rates: rates
     * that change during a month come with step 9b).
     *
     * @param month null = this month
     */
    @Transactional(readOnly = true)
    public LabourMonth month(Long plantId, YearMonth month) {
        PlantAccess.require(plantId);
        LocalDate today = today();
        YearMonth ym = month == null ? YearMonth.from(today) : month;
        LocalDate from = ym.atDay(1);
        if (from.isAfter(today)) {
            throw new BusinessException("That month hasn't started");
        }
        LocalDate to = ym.atEndOfMonth().isAfter(today) ? today : ym.atEndOfMonth();

        Map<Worker, List<Attendance>> byWorker = attendanceRepository.between(plantId, from, to).stream()
                .collect(Collectors.groupingBy(Attendance::getWorker, LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<LabourMonth.WorkerMonth>> byContractor = new LinkedHashMap<>();
        Map<Long, Party> contractors = new LinkedHashMap<>();
        Map<Long, Integer> unpriced = new LinkedHashMap<>();
        Map<Department, BigDecimal> departments = new EnumMap<>(Department.class);

        List<Worker> workers = byWorker.keySet().stream()
                .sorted(Comparator.comparing(Worker::getDepartment).thenComparing(Worker::getCode))
                .toList();
        for (Worker w : workers) {
            LabourMonth.WorkerMonth wm = workerMonth(w, byWorker.get(w));
            Long key = w.getContractor() == null ? null : w.getContractor().getId();
            if (key != null) {
                contractors.putIfAbsent(key, w.getContractor());
            }
            byContractor.computeIfAbsent(key, k -> new ArrayList<>()).add(wm);
            boolean noDayRate = wm.manDays().signum() > 0 && w.getDayRate() == null;
            boolean noOtRate = wm.otHours().signum() > 0 && w.getOtRate() == null;
            unpriced.merge(key, noDayRate || noOtRate ? 1 : 0, Integer::sum);
            departments.merge(w.getDepartment(), wm.manDays(), BigDecimal::add);
        }

        List<LabourMonth.ContractorMonth> rows = new ArrayList<>();
        byContractor.forEach((id, list) -> rows.add(new LabourMonth.ContractorMonth(id,
                id == null ? null : contractors.get(id).getName(), list.size(),
                sum(list, LabourMonth.WorkerMonth::manDays), sum(list, LabourMonth.WorkerMonth::otHours),
                sum(list, LabourMonth.WorkerMonth::amount), unpriced.get(id), list)));
        // contractors by name, the plant's own rolls last
        rows.sort(Comparator.comparing(LabourMonth.ContractorMonth::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));

        return new LabourMonth(ym.toString(), from, to, rows,
                departments.entrySet().stream()
                        .filter(e -> e.getValue().signum() > 0)
                        .map(e -> new LabourMonth.DepartmentDays(e.getKey(), e.getValue()))
                        .toList(),
                sum(rows, LabourMonth.ContractorMonth::manDays), sum(rows, LabourMonth.ContractorMonth::otHours),
                sum(rows, LabourMonth.ContractorMonth::amount));
    }

    // ------------------------------------------------------------------ helpers

    /** The production date running now: the night shift after midnight still belongs to yesterday. */
    LocalDate today() {
        LocalDateTime now = LocalDateTime.now(PLANT_ZONE);
        return shiftService.current(now).map(ShiftService.CurrentShift::prodDate).orElse(now.toLocalDate());
    }

    private Map<Long, Attendance> marksOn(Long plantId, LocalDate day) {
        return attendanceRepository.onDay(plantId, day).stream()
                .collect(Collectors.toMap(a -> a.getWorker().getId(), Function.identity()));
    }

    /** Was the worker on the rolls that day: still working here, and joined by then. */
    private static boolean onRolls(Worker w, LocalDate day) {
        return w.isActive() && (w.getJoinedOn() == null || !w.getJoinedOn().isAfter(day));
    }

    private static AttendanceSheet.Row row(Worker w, Attendance a) {
        AttendanceSheet.Mark mark = a == null ? null : new AttendanceSheet.Mark(a.getId(), a.getStatus(), a.getOtHours(),
                a.getShift() == null ? null : a.getShift().getId(), a.getShift() == null ? null : a.getShift().getCode(),
                a.getInTime(), a.getOutTime(), a.getSource(), a.getNote(), a.getUpdatedBy(), a.getUpdatedAt());
        return new AttendanceSheet.Row(w.getId(), w.getCode(), w.getName(), w.getDepartment(), w.getJob(), w.getPunch(),
                w.getContractor() == null ? null : w.getContractor().getName(), mark);
    }

    private static AttendanceSheet.Counts counts(List<AttendanceSheet.Row> rows) {
        Map<AttendanceStatus, Long> by = rows.stream().filter(r -> r.mark() != null)
                .collect(Collectors.groupingBy(r -> r.mark().status(), () -> new EnumMap<>(AttendanceStatus.class),
                        Collectors.counting()));
        int marked = by.values().stream().mapToInt(Long::intValue).sum();
        return new AttendanceSheet.Counts(rows.size(), by.getOrDefault(AttendanceStatus.PRESENT, 0L).intValue(),
                by.getOrDefault(AttendanceStatus.HALF, 0L).intValue(), by.getOrDefault(AttendanceStatus.ABSENT, 0L).intValue(),
                by.getOrDefault(AttendanceStatus.LEAVE, 0L).intValue(), rows.size() - marked);
    }

    /** One worker's month: days of each kind, man-days, overtime, and the amount at their rates. */
    private static LabourMonth.WorkerMonth workerMonth(Worker w, List<Attendance> days) {
        Map<AttendanceStatus, Long> by = days.stream()
                .collect(Collectors.groupingBy(Attendance::getStatus, () -> new EnumMap<>(AttendanceStatus.class),
                        Collectors.counting()));
        BigDecimal manDays = days.stream().map(a -> a.getStatus().getDays()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal ot = days.stream().map(Attendance::getOtHours).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal amount = BigDecimal.ZERO;
        if (w.getDayRate() != null) {
            amount = amount.add(manDays.multiply(w.getDayRate()));
        }
        if (w.getOtRate() != null) {
            amount = amount.add(ot.multiply(w.getOtRate()));
        }
        return new LabourMonth.WorkerMonth(w.getId(), w.getCode(), w.getName(), w.getDepartment(),
                by.getOrDefault(AttendanceStatus.PRESENT, 0L).intValue(), by.getOrDefault(AttendanceStatus.HALF, 0L).intValue(),
                by.getOrDefault(AttendanceStatus.ABSENT, 0L).intValue(), by.getOrDefault(AttendanceStatus.LEAVE, 0L).intValue(),
                manDays, ot, w.getDayRate(), w.getOtRate(), amount.setScale(2, RoundingMode.HALF_UP));
    }

    private static <T> BigDecimal sum(List<T> list, Function<T, BigDecimal> value) {
        return list.stream().map(value).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
