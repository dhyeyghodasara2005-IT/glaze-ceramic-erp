package com.ceramicerp.maintenance;

import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.maintenance.dto.DowntimeResponse;
import com.ceramicerp.maintenance.dto.MachineRef;
import com.ceramicerp.master.Machine;
import com.ceramicerp.master.MachineRepository;
import com.ceramicerp.security.PlantAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * How much each machine was down in a period, and the two numbers maintenance teams everywhere use:
 * <ul>
 *   <li><b>MTTR</b> (mean time to repair): how long a breakdown takes to fix, on average;</li>
 *   <li><b>MTBF</b> (mean time between failures): how long the machine runs between two breakdowns.</li>
 * </ul>
 * A breakdown that started before the period, or is still open, only counts the minutes inside the period.
 */
@Service
@RequiredArgsConstructor
public class DowntimeService {

    private static final int MAX_DAYS = 366;

    private final BreakdownRepository breakdownRepository;
    private final MachineRepository machineRepository;

    @Transactional(readOnly = true)
    public DowntimeResponse downtime(Long plantId, LocalDate from, LocalDate to) {
        PlantAccess.require(plantId);
        if (from.isAfter(to)) {
            throw new BusinessException("The period starts after it ends");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw new BusinessException("Choose a period of at most a year");
        }
        LocalDateTime now = BreakdownService.now();
        LocalDateTime start = from.atStartOfDay();
        // today counts up to now: a machine can't have run in hours that haven't happened yet
        LocalDateTime end = to.plusDays(1).atStartOfDay().isAfter(now) ? now : to.plusDays(1).atStartOfDay();
        long periodMinutes = Math.max(1, Duration.between(start, end).toMinutes());

        List<Breakdown> breakdowns = breakdownRepository.overlapping(plantId, start, end);
        // "down now" is about now, whatever period is being looked at
        List<Breakdown> open = breakdownRepository.findByPlantIdAndStatusOrderByStoppedAtAsc(plantId, BreakdownStatus.OPEN);
        Set<Long> downNowIds = open.stream().map(b -> b.getMachine().getId()).collect(Collectors.toSet());
        Map<Long, List<Breakdown>> byMachine = breakdowns.stream().collect(Collectors.groupingBy(b -> b.getMachine().getId()));

        // every active machine is listed, also those that never stopped (100%), plus inactive ones that did stop
        List<Machine> machines = new ArrayList<>(machineRepository.findByPlantIdInProcessOrder(plantId).stream()
                .filter(m -> m.isActive() || byMachine.containsKey(m.getId())).toList());

        List<DowntimeResponse.MachineDowntime> rows = new ArrayList<>();
        Map<BreakdownCategory, long[]> categories = new EnumMap<>(BreakdownCategory.class);
        for (Machine m : machines) {
            List<Breakdown> list = byMachine.getOrDefault(m.getId(), List.of());
            long down = list.stream().mapToLong(b -> inside(b, start, end, now)).sum();
            List<Breakdown> started = list.stream().filter(b -> !b.getStoppedAt().isBefore(start)).toList();
            List<Breakdown> closed = started.stream().filter(b -> b.getDowntimeMinutes() != null).toList();
            Integer mttr = closed.isEmpty() ? null
                    : (int) Math.round(closed.stream().mapToInt(Breakdown::getDowntimeMinutes).average().orElse(0));
            BigDecimal mtbf = started.isEmpty() ? null
                    : BigDecimal.valueOf(periodMinutes - down).divide(BigDecimal.valueOf(60L * started.size()), 1, RoundingMode.HALF_UP);
            rows.add(new DowntimeResponse.MachineDowntime(MachineRef.of(m), started.size(), down, mttr, mtbf,
                    pct(periodMinutes - down, periodMinutes), downNowIds.contains(m.getId())));
            for (Breakdown b : list) {
                // an open breakdown has no category yet: it counts as OTHER until maintenance closes it
                BreakdownCategory c = b.getCategory() == null ? BreakdownCategory.OTHER : b.getCategory();
                long[] acc = categories.computeIfAbsent(c, k -> new long[2]);
                acc[0] += b.getStoppedAt().isBefore(start) ? 0 : 1;
                acc[1] += inside(b, start, end, now);
            }
        }
        // the machine that lost the most time first
        rows.sort(Comparator.comparingLong(DowntimeResponse.MachineDowntime::downtimeMinutes).reversed()
                .thenComparing(r -> r.machine().code()));

        List<DowntimeResponse.CategoryDowntime> byCategory = categories.entrySet().stream()
                .map(e -> new DowntimeResponse.CategoryDowntime(e.getKey(), (int) e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.comparingLong(DowntimeResponse.CategoryDowntime::minutes).reversed())
                .toList();
        List<DowntimeResponse.DownNow> downNow = open.stream()
                .map(b -> new DowntimeResponse.DownNow(b.getId(), b.getBreakdownNo(), MachineRef.of(b.getMachine()),
                        b.getStoppedAt(), BreakdownService.minutes(b, now), b.getSymptom()))
                .toList();

        return new DowntimeResponse(from, to, periodMinutes,
                rows.stream().mapToInt(DowntimeResponse.MachineDowntime::breakdowns).sum(),
                rows.stream().mapToLong(DowntimeResponse.MachineDowntime::downtimeMinutes).sum(),
                rows, byCategory, downNow);
    }

    /** The minutes of a breakdown that fall inside [start, end): open ones run until now. */
    static long inside(Breakdown b, LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        LocalDateTime from = b.getStoppedAt().isBefore(start) ? start : b.getStoppedAt();
        LocalDateTime until = b.getRestartedAt() != null ? b.getRestartedAt() : now;
        if (until.isAfter(end)) {
            until = end;
        }
        return Math.max(0, Duration.between(from, until).toMinutes());
    }

    private static BigDecimal pct(long part, long whole) {
        return whole == 0 ? null
                : BigDecimal.valueOf(part * 100).divide(BigDecimal.valueOf(whole), 1, RoundingMode.HALF_UP);
    }
}
