package com.ceramicerp.costing;

import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.costing.dto.CostSheet;
import com.ceramicerp.costing.dto.CostTrend;
import com.ceramicerp.energy.EnergyService;
import com.ceramicerp.energy.MeterKind;
import com.ceramicerp.energy.MeterUnit;
import com.ceramicerp.energy.dto.CostableUse;
import com.ceramicerp.labour.AttendanceService;
import com.ceramicerp.labour.dto.LabourMonth;
import com.ceramicerp.security.PlantAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * What a m² cost in a month: what left the store, the energy used at the rate of its day, the workers' days, and the
 * monthly costs, divided by the m² packed. Each part is read from the module that records it; nothing is typed twice.
 * <p>
 * It is the month's spending against the month's packing. Material issued this month may be packed next month, so
 * single months move a little; the trend evens that out.
 */
@Service
@RequiredArgsConstructor
public class CostSheetService {

    /** Fuel is costed from the meters: fuel issues from the store would count it twice. */
    private static final String FUEL_GROUP = "FUEL";
    private static final int MAX_TREND = 12;

    private final CostQueries queries;
    private final CostRateRepository rateRepository;
    private final MonthlyCostRepository monthlyRepository;
    private final EnergyService energyService;
    private final AttendanceService attendanceService;

    /** @param month null = this month */
    @Transactional(readOnly = true)
    public CostSheet sheet(Long plantId, YearMonth month) {
        PlantAccess.require(plantId);
        LocalDate today = attendanceService.today();
        YearMonth ym = month == null ? YearMonth.from(today) : month;
        if (ym.atDay(1).isAfter(today)) {
            throw new BusinessException("That month hasn't started");
        }
        return build(plantId, ym, today);
    }

    /** The latest months, oldest first. */
    @Transactional(readOnly = true)
    public List<CostTrend> trend(Long plantId, int months) {
        PlantAccess.require(plantId);
        LocalDate today = attendanceService.today();
        YearMonth latest = YearMonth.from(today);
        List<CostTrend> out = new ArrayList<>();
        for (int i = Math.min(Math.max(months, 1), MAX_TREND) - 1; i >= 0; i--) {
            CostSheet s = build(plantId, latest.minusMonths(i), today);
            out.add(new CostTrend(s.month(), s.total(), s.sqmPacked(), s.perSqm()));
        }
        return out;
    }

    private CostSheet build(Long plantId, YearMonth ym, LocalDate today) {
        LocalDate from = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        LocalDate to = end.isAfter(today) ? today : end;
        CostQueries.Packed packed = queries.packed(plantId, from, to);

        List<Draft> drafts = new ArrayList<>();
        BigDecimal fuelInStore = material(plantId, from, to, drafts);
        energy(plantId, from, to, drafts);
        labour(plantId, ym, drafts);
        monthly(plantId, from, drafts);

        BigDecimal total = drafts.stream().map(d -> d.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<CostSheet.Line> lines = drafts.stream().map(d -> d.line(packed.sqm(), total)).toList();
        return new CostSheet(ym.toString(), from, to, end.isAfter(today), packed.sqm(), packed.boxes(), total,
                per(total, packed.sqm()), packed.boxes() == 0 ? null : per(total, BigDecimal.valueOf(packed.boxes())), lines, fuelInStore);
    }

    /** Store issues per material group, the biggest first. Returns the fuel issued, which isn't added. */
    private BigDecimal material(Long plantId, LocalDate from, LocalDate to, List<Draft> drafts) {
        BigDecimal fuel = null;
        List<CostQueries.Issued> issued = new ArrayList<>(queries.issuedByGroup(plantId, from, to));
        issued.sort(Comparator.comparing(CostQueries.Issued::value).reversed());
        for (CostQueries.Issued i : issued) {
            if (FUEL_GROUP.equalsIgnoreCase(i.groupCode())) {
                fuel = i.value();
                continue;
            }
            Draft d = new Draft(CostSheet.Section.MATERIAL, i.groupCode());
            d.name = i.groupName();
            d.amount = i.value();
            drafts.add(d);
        }
        return fuel;
    }

    /** Each fuel used, every day's use at the rate of that day. A day before the first rate stays unpriced. */
    private void energy(Long plantId, LocalDate from, LocalDate to, List<Draft> drafts) {
        CostableUse use = energyService.costableUse(plantId, from, to);
        Map<RateKey, TreeMap<LocalDate, BigDecimal>> rates = new HashMap<>();
        for (CostRate r : rateRepository.findByPlantIdOrderByKindAscUnitAscValidFromDesc(plantId)) {
            rates.computeIfAbsent(new RateKey(r.getKind(), r.getUnit()), k -> new TreeMap<>()).put(r.getValidFrom(), r.getRate());
        }
        Map<RateKey, Draft> byFuel = new TreeMap<>(Comparator.comparing(RateKey::kind).thenComparing(RateKey::unit));
        for (CostableUse.Use u : use.uses()) {
            RateKey key = new RateKey(u.kind(), u.unit());
            Draft d = byFuel.computeIfAbsent(key, k -> new Draft(CostSheet.Section.ENERGY, k.kind().name()));
            d.unit = u.unit();
            d.quantity = d.quantity.add(u.used());
            TreeMap<LocalDate, BigDecimal> ofFuel = rates.get(key);
            Map.Entry<LocalDate, BigDecimal> rate = ofFuel == null ? null : ofFuel.floorEntry(u.day());
            if (rate == null) {
                d.unpriced = d.unpriced.add(u.used());
            } else {
                d.amount = d.amount.add(u.used().multiply(rate.getValue()));
            }
        }
        byFuel.values().stream().filter(d -> d.quantity.signum() > 0).forEach(d -> {
            MeterKind kind = MeterKind.valueOf(d.code);
            d.fromSections = use.fromSections().contains(kind);
            d.missing = use.missing().getOrDefault(kind, 0);
            d.amount = d.amount.setScale(2, RoundingMode.HALF_UP);
            drafts.add(d);
        });
    }

    /** Workers' days at their rates: those through contractors, and those on the plant's own rolls. */
    private void labour(Long plantId, YearMonth ym, List<Draft> drafts) {
        LabourMonth month = attendanceService.month(plantId, ym);
        Draft contract = new Draft(CostSheet.Section.LABOUR, "CONTRACT");
        Draft own = new Draft(CostSheet.Section.LABOUR, "OWN_ROLLS");
        for (LabourMonth.ContractorMonth c : month.contractors()) {
            Draft d = c.contractorId() == null ? own : contract;
            d.quantity = d.quantity.add(c.manDays());
            d.amount = d.amount.add(c.amount());
            d.unpricedWorkers += c.unpriced();
        }
        for (Draft d : List.of(contract, own)) {
            if (d.quantity.signum() > 0 || d.amount.signum() > 0) {
                drafts.add(d);
            }
        }
    }

    private void monthly(Long plantId, LocalDate from, List<Draft> drafts) {
        monthlyRepository.findByPlantIdAndMonthStart(plantId, from).stream()
                .sorted(Comparator.comparing(MonthlyCost::getHead))
                .forEach(c -> {
                    Draft d = new Draft(CostSheet.Section.MONTHLY, c.getHead().name());
                    d.amount = c.getAmount();
                    drafts.add(d);
                });
    }

    /** null when there is nothing to divide by. */
    private static BigDecimal per(BigDecimal amount, BigDecimal base) {
        return base == null || base.signum() == 0 ? null : amount.divide(base, 2, RoundingMode.HALF_UP);
    }

    private record RateKey(MeterKind kind, MeterUnit unit) {
    }

    /** A line while it is being added up. */
    private static final class Draft {
        final CostSheet.Section section;
        final String code;
        String name;
        MeterUnit unit;
        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal unpriced = BigDecimal.ZERO;
        int unpricedWorkers;
        int missing;
        boolean fromSections;

        Draft(CostSheet.Section section, String code) {
            this.section = section;
            this.code = code;
        }

        CostSheet.Line line(BigDecimal sqm, BigDecimal total) {
            BigDecimal priced = quantity.subtract(unpriced);
            boolean energy = section == CostSheet.Section.ENERGY;
            return new CostSheet.Line(section, code, name, quantity.signum() == 0 ? null : quantity, unit,
                    energy && priced.signum() > 0 && amount.signum() > 0 ? amount.divide(priced, 4, RoundingMode.HALF_UP) : null,
                    amount, per(amount, sqm),
                    total.signum() == 0 ? null : amount.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP),
                    unpriced.signum() == 0 ? null : unpriced, unpricedWorkers == 0 ? null : unpricedWorkers,
                    missing == 0 ? null : missing, fromSections ? Boolean.TRUE : null);
        }
    }
}
