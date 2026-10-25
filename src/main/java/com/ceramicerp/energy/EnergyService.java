package com.ceramicerp.energy;

import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.energy.dto.EnergyUse;
import com.ceramicerp.energy.dto.MeterResponse;
import com.ceramicerp.master.MachineType;
import com.ceramicerp.master.Shift;
import com.ceramicerp.security.PlantAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * What the plant burned on the production days of a period: from the main meters (what the bills are on), the
 * section and kiln meters, the coal, LPG and diesel; and per m² packed, per m² fired, per tonne of powder.
 */
@Service
@RequiredArgsConstructor
public class EnergyService {

    private static final int MAX_DAYS = 366;

    private final MeterRepository meterRepository;
    private final MeterReadingRepository readingRepository;
    private final EnergyQueries queries;
    private final Rounds rounds;

    @Transactional(readOnly = true)
    public EnergyUse use(Long plantId, LocalDate from, LocalDate to) {
        PlantAccess.require(plantId);
        if (from.isAfter(to)) {
            throw new BusinessException("The period starts after it ends");
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_DAYS) {
            throw new BusinessException("Choose a period of at most a year");
        }
        List<Meter> meters = meterRepository.findByPlantIdOrderBySortOrderAscCodeAsc(plantId);
        List<MeterReading> readings = readingRepository.usedBetween(plantId, from, to);
        Map<Long, List<MeterReading>> byMeter = readings.stream().collect(Collectors.groupingBy(r -> r.getMeter().getId()));

        Predicate<Meter> mainGas = m -> m.getKind() == MeterKind.GAS && m.isMain();
        Predicate<Meter> kilnGas = EnergyService::onKiln;
        Predicate<Meter> mainPower = m -> m.getKind() == MeterKind.POWER && m.isMain();

        // the totals, and what they mean per m² and per tonne
        BigDecimal gasMain = used(readings, mainGas);
        BigDecimal gasKiln = used(readings, kilnGas);
        BigDecimal powerMain = used(readings, mainPower);
        Set<Long> meteredKilns = meters.stream().filter(kilnGas).map(m -> m.getMachine().getId()).collect(Collectors.toSet());
        BigDecimal fired = gasKiln == null ? null : queries.sqmFired(plantId, from, to, meteredKilns);
        BigDecimal packed = queries.sqmPacked(plantId, from, to);
        BigDecimal coal = used(readings, m -> m.getKind() == MeterKind.COAL);
        BigDecimal powder = coal == null ? null : queries.powderTon(plantId, from, to);
        BigDecimal hours = used(readings, m -> m.getKind() == MeterKind.RUN_HOURS);
        BigDecimal diesel = used(readings, m -> m.getKind() == MeterKind.DIESEL);
        EnergyUse.Totals totals = new EnergyUse.Totals(gasMain, unitOf(meters, mainGas), gasKiln, unitOf(meters, kilnGas),
                scale(fired), per(gasKiln, fired), powerMain, scale(packed), per(powerMain, packed), per(gasMain, packed),
                coal, scale(powder), per(coal, powder), used(readings, m -> m.getKind() == MeterKind.PROPANE),
                used(readings, m -> m.getKind() == MeterKind.LNG), hours, diesel, per(diesel, hours));

        // per production day, for the charts
        List<EnergyUse.Day> perDay = new ArrayList<>();
        Map<LocalDate, List<MeterReading>> byDay = readings.stream().collect(Collectors.groupingBy(MeterReading::getReadOn));
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            List<MeterReading> day = byDay.getOrDefault(d, List.of());
            perDay.add(new EnergyUse.Day(d, used(day, mainGas), used(day, kilnGas), used(day, mainPower)));
        }

        // each meter: what it counted, and how many of its readings are there
        Rounds.Slot last = rounds.lastFinished();
        LocalDate today = rounds.today();
        List<Shift> shifts = rounds.activeShifts();
        List<EnergyUse.MeterUse> perMeter = new ArrayList<>();
        for (Meter m : meters) {
            List<MeterReading> mine = byMeter.getOrDefault(m.getId(), List.of());
            if (!m.isActive() && mine.isEmpty()) {
                continue;
            }
            BigDecimal used = mine.isEmpty() ? null : sum(mine.stream().map(MeterReading::getUsed).toList());
            MeterReading latest = first(readingRepository.latest(m.getId(), PageRequest.of(0, 1)));
            perMeter.add(new EnergyUse.MeterUse(MeterResponse.from(m), used,
                    used == null ? null : used.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP),
                    mine.size(), m.isActive() && m.getKind().isEveryRound() ? expected(m, inUseSince(m), from, to, last, today, shifts) : mine.size(),
                    latest == null ? null : new EnergyUse.Last(latest.getReading(), latest.getUsed(), latest.getReadOn(),
                            latest.getShift() == null ? null : latest.getShift().getCode())));
        }

        // power by section, and what the main meter counted beyond the section meters
        List<EnergyUse.Part> parts = new ArrayList<>();
        BigDecimal sections = BigDecimal.ZERO;
        for (Meter m : meters) {
            if (m.getKind() == MeterKind.POWER && !m.isMain() && byMeter.containsKey(m.getId())) {
                BigDecimal kwh = sum(byMeter.get(m.getId()).stream().map(MeterReading::getUsed).toList());
                parts.add(new EnergyUse.Part(m.getId(), m.getName(), kwh));
                sections = sections.add(kwh);
            }
        }
        if (powerMain != null && !parts.isEmpty()) {
            parts.add(new EnergyUse.Part(null, null, powerMain.subtract(sections)));
        }

        return new EnergyUse(from, to, totals, perDay, perMeter, parts, lastRound(plantId, meters, last, today));
    }

    /** The meters not read yet for the round that ended last (for the dashboard's "needs a look"). */
    @Transactional(readOnly = true)
    public EnergyUse.LastRound lastRound(Long plantId) {
        PlantAccess.require(plantId);
        return lastRound(plantId, meterRepository.findByPlantIdOrderBySortOrderAscCodeAsc(plantId), rounds.lastFinished(),
                rounds.today());
    }

    /** Kiln gas (with the m² those kilns fired) and main power, from the meters; null where nothing was read. */
    public record DashboardFigures(BigDecimal kilnGas, MeterUnit kilnGasUnit, BigDecimal sqmFired, BigDecimal powerMain) {
    }

    @Transactional(readOnly = true)
    public DashboardFigures dashboardFigures(Long plantId, LocalDate from, LocalDate to) {
        List<MeterReading> readings = readingRepository.usedBetween(plantId, from, to);
        BigDecimal gas = used(readings, EnergyService::onKiln);
        List<Meter> kilnMeters = readings.stream().map(MeterReading::getMeter).filter(EnergyService::onKiln).distinct().toList();
        return new DashboardFigures(gas, kilnMeters.isEmpty() ? null : kilnMeters.get(0).getUnit(),
                gas == null ? null : scale(queries.sqmFired(plantId, from, to,
                        kilnMeters.stream().map(m -> m.getMachine().getId()).collect(Collectors.toSet()))),
                used(readings, m -> m.getKind() == MeterKind.POWER && m.isMain()));
    }

    // ------------------------------------------------------------------ helpers

    /**
     * The shift meters not read for the shift that ended last, and the once-a-day meters not read for yesterday
     * (they were due this morning).
     */
    private EnergyUse.LastRound lastRound(Long plantId, List<Meter> meters, Rounds.Slot last, LocalDate today) {
        Set<Long> readForShift = last.shift() == null ? Set.of()
                : readingRepository.inRound(plantId, last.date(), last.shift().getId()).stream()
                .filter(r -> r.getShift() != null).map(r -> r.getMeter().getId()).collect(Collectors.toSet());
        LocalDate yesterday = today.minusDays(1);
        Set<Long> readForYesterday = readingRepository.inRound(plantId, yesterday, null).stream()
                .filter(r -> r.getShift() == null).map(r -> r.getMeter().getId()).collect(Collectors.toSet());
        List<String> missing = meters.stream()
                .filter(m -> m.isActive() && m.getKind().isEveryRound() && m.getReadEvery() == ReadEvery.SHIFT)
                .filter(m -> last.shift() != null && !readForShift.contains(m.getId())
                        && !inUseSince(m).isAfter(last.date()))
                .map(Meter::getName)
                .toList();
        List<String> dayMissing = meters.stream()
                .filter(m -> m.isActive() && m.getKind().isEveryRound() && m.getReadEvery() == ReadEvery.DAY)
                .filter(m -> !readForYesterday.contains(m.getId()) && inUseSince(m).isBefore(today))
                .map(Meter::getName)
                .toList();
        return new EnergyUse.LastRound(last.date(), last.shift() == null ? null : last.shift().getId(),
                last.shift() == null ? null : last.shift().getCode(), missing, yesterday, dayMissing);
    }

    /**
     * The day a meter came into use: when it was set up, or its first reading if that is earlier (readings typed in
     * from a register). It isn't missing readings from before then.
     */
    private LocalDate inUseSince(Meter m) {
        LocalDate set = m.getCreatedAt() == null ? LocalDate.MIN : m.getCreatedAt().atZone(Rounds.PLANT_ZONE).toLocalDate();
        return readingRepository.findFirstByMeterIdOrderByReadOnAsc(m.getId())
                .map(MeterReading::getReadOn)
                .filter(first -> first.isBefore(set))
                .orElse(set);
    }

    /**
     * How many readings a meter should have for the period's production days, counting only rounds that have ended,
     * from the day it came into use: one per day (read the morning after, so not yet for today), or one per shift.
     */
    private static int expected(Meter m, LocalDate since, LocalDate from, LocalDate to, Rounds.Slot last, LocalDate today,
                                List<Shift> shifts) {
        LocalDate start = later(from, since);
        int count = 0;
        for (LocalDate d = start; !d.isAfter(to); d = d.plusDays(1)) {
            if (m.getReadEvery() == ReadEvery.DAY) {
                if (d.isBefore(today)) {
                    count++;
                }
            } else {
                for (Shift s : shifts) {
                    if (new Rounds.Slot(d, s).endedBy(last)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static boolean onKiln(Meter m) {
        return m.getKind() == MeterKind.GAS && m.getMachine() != null && m.getMachine().getType() == MachineType.KILN;
    }

    private static BigDecimal used(Collection<MeterReading> readings, Predicate<Meter> which) {
        List<BigDecimal> uses = readings.stream().filter(r -> which.test(r.getMeter())).map(MeterReading::getUsed).toList();
        return uses.isEmpty() ? null : sum(uses);
    }

    private static MeterUnit unitOf(List<Meter> meters, Predicate<Meter> which) {
        return meters.stream().filter(which).map(Meter::getUnit).findFirst().orElse(null);
    }

    private static BigDecimal per(BigDecimal amount, BigDecimal base) {
        return amount == null || base == null || base.signum() == 0 ? null : amount.divide(base, 3, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale(BigDecimal n) {
        return n == null ? null : n.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(List<BigDecimal> list) {
        return list.stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static LocalDate later(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static <T> T first(List<T> list) {
        return list.isEmpty() ? null : list.get(0);
    }
}
