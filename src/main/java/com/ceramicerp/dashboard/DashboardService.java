package com.ceramicerp.dashboard;

import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.dashboard.DashboardQueries.DispatchRow;
import com.ceramicerp.dashboard.DashboardQueries.GradeRow;
import com.ceramicerp.dashboard.DashboardQueries.StageRow;
import com.ceramicerp.dashboard.dto.DashboardResponse;
import com.ceramicerp.dashboard.dto.DashboardResponse.Attention;
import com.ceramicerp.fg.FgPillar;
import com.ceramicerp.fg.FgStock;
import com.ceramicerp.fg.FgStockRepository;
import com.ceramicerp.fg.dto.FgStockRow;
import com.ceramicerp.energy.EnergyService;
import com.ceramicerp.energy.dto.EnergyUse;
import com.ceramicerp.lab.LabService;
import com.ceramicerp.lab.dto.LabSample;
import com.ceramicerp.maintenance.DowntimeService;
import com.ceramicerp.maintenance.PmTaskService;
import com.ceramicerp.maintenance.dto.DowntimeResponse;
import com.ceramicerp.maintenance.dto.PmOverview;
import com.ceramicerp.master.Grade;
import com.ceramicerp.master.GradeRepository;
import com.ceramicerp.production.LineStage;
import com.ceramicerp.security.CurrentUser;
import com.ceramicerp.security.PlantAccess;
import com.ceramicerp.user.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The owner's dashboard: what was packed and dispatched in a period, how the line ran, what stands in the godown,
 * and what needs a look. Each section is filled only for users who may see that module.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    /** A year at most: the daily chart and the queries stay quick. */
    private static final int MAX_DAYS = 366;
    private static final int TOP_DESIGNS = 5;

    private final DashboardQueries queries;
    private final FgStockRepository stockRepository;
    private final GradeRepository gradeRepository;
    private final DowntimeService downtimeService;
    private final PmTaskService pmTaskService;
    private final EnergyService energyService;
    private final LabService labService;

    @Transactional(readOnly = true)
    public DashboardResponse get(Long plantId, LocalDate from, LocalDate to) {
        PlantAccess.require(plantId);
        if (from.isAfter(to)) {
            throw new BusinessException("The period starts after it ends");
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_DAYS) {
            throw new BusinessException("Choose a period of at most a year");
        }
        Set<Permission> may = CurrentUser.get().getPermissions();
        boolean production = may.contains(Permission.PRODUCTION_VIEW);
        boolean fg = may.contains(Permission.FG_VIEW);
        boolean sales = may.contains(Permission.SALES_VIEW);
        boolean store = may.contains(Permission.STORE_VIEW);
        boolean maintenance = may.contains(Permission.MAINTENANCE_VIEW);
        boolean energy = may.contains(Permission.ENERGY_VIEW) || may.contains(Permission.METER_ENTRY);
        boolean lab = may.contains(Permission.LAB_VIEW) || may.contains(Permission.LAB_ENTRY);

        List<Attention> attention = new ArrayList<>();
        // a machine standing still is the most urgent thing of all: it goes first
        DashboardResponse.Maintenance maint = maintenance ? maintenance(plantId, from, to, attention) : null;
        DashboardResponse.Production prod = production ? production(plantId, from, to, days) : null;
        DashboardResponse.FinishedGoods goods = fg ? finishedGoods(plantId, attention) : null;
        List<DispatchRow> dispatched = sales ? queries.dispatchedByDay(plantId, from, to) : List.of();
        DashboardResponse.Dispatch dispatch = sales ? dispatch(plantId, dispatched, attention) : null;
        DashboardResponse.Store st = store ? store(plantId, attention) : null;
        if (lab) {
            // material standing at the gate (or in the yard, not in stock) until the lab has tested it
            List<LabSample> waiting = labService.waiting(plantId);
            if (!waiting.isEmpty()) {
                attention.add(new Attention(Attention.Kind.LAB_WAITING, waiting.stream().map(LabSample::materialName)
                        .distinct().collect(Collectors.joining(", ")), waiting.size(), null));
            }
        }
        if (energy) {
            EnergyUse.LastRound round = energyService.lastRound(plantId);
            List<String> missing = new ArrayList<>(round.missing());
            missing.addAll(round.dayMissing());
            if (!missing.isEmpty()) {
                attention.add(new Attention(Attention.Kind.METER_MISSING, String.join(", ", missing), missing.size(), null));
            }
        }

        return new DashboardResponse(from, to, prod, goods, dispatch, st, maint,
                days(from, to, production ? queries.packedByDay(plantId, from, to) : null, sales ? dispatched : null),
                attention);
    }

    // ------------------------------------------------------------------ production

    private DashboardResponse.Production production(Long plantId, LocalDate from, LocalDate to, long days) {
        List<GradeRow> grades = queries.packedByGrade(plantId, from, to);
        int boxes = (int) grades.stream().mapToLong(GradeRow::boxes).sum();
        BigDecimal sqm = sum(grades.stream().map(GradeRow::sqm).toList());
        // the same number of days just before, for "+12% on the previous 7 days"
        BigDecimal before = sum(queries.packedByGrade(plantId, from.minusDays(days), from.minusDays(1)).stream()
                .map(GradeRow::sqm).toList());

        // the best grade a tile can be sold as (Premium): its share of the boxes
        Grade top = gradeRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .filter(g -> g.isActive() && g.isSellable())
                .findFirst().orElse(null);
        long topBoxes = top == null ? 0 : grades.stream().filter(g -> g.code().equals(top.getCode()))
                .mapToLong(GradeRow::boxes).sum();

        // pieces at each stage, from each stage's own entries in the period
        var press = queries.pressTotals(plantId, from, to);
        Map<LineStage, StageRow> stages = queries.stageTotals(plantId, from, to).stream()
                .collect(Collectors.toMap(StageRow::stage, Function.identity()));
        var pack = queries.packTotals(plantId, from, to);
        List<DashboardResponse.Stage> funnel = List.of(
                new DashboardResponse.Stage("PRESSED", press.pressed(), press.entries() > 0),
                new DashboardResponse.Stage("DRIED", press.pressed() - press.rejects(), press.entries() > 0),
                stage("GLAZED", stages.get(LineStage.GLAZE)),
                stage("FIRED", stages.get(LineStage.KILN)),
                stage("POLISHED", stages.get(LineStage.POLISH)),
                new DashboardResponse.Stage("PACKED", pack.packed(), pack.sorted() > 0));

        // kiln gas per m² that came out of the kiln; power per m² packed. The meters (step 8c) come first: the kiln's
        // own gas meter against what those kilns fired, and the GEB main meter. Without readings, what the shift
        // entries say (gas in the kiln entries, power in the polishing entries).
        EnergyService.DashboardFigures metered = energyService.dashboardFigures(plantId, from, to);
        BigDecimal fired;
        BigDecimal gas;
        String gasUnit;
        if (metered.kilnGas() != null) {
            fired = metered.sqmFired();
            gas = metered.kilnGas();
            gasUnit = metered.kilnGasUnit().name();
        } else {
            fired = sum(queries.firedBySize(plantId, from, to).stream()
                    .map(r -> r.size().sqmPerTile() == null ? null : r.size().sqmPerTile().multiply(BigDecimal.valueOf(r.tiles())))
                    .toList());
            StageRow kiln = stages.get(LineStage.KILN);
            gas = kiln == null ? null : kiln.gasScm();
            gasUnit = "SCM";
        }
        BigDecimal power = metered.powerMain() != null ? metered.powerMain()
                : sum(stages.values().stream().map(StageRow::powerKwh).filter(Objects::nonNull).toList());

        return new DashboardResponse.Production(boxes, scale(sqm), scale(before), top == null ? null : top.getName(),
                pct(topBoxes, boxes), shares(grades, boxes), funnel, pct(pack.packed(), press.pressed()), gas, gasUnit,
                scale(fired), per(gas, fired), power.signum() == 0 ? null : power, per(power, sqm));
    }

    private static DashboardResponse.Stage stage(String key, StageRow row) {
        return row == null ? new DashboardResponse.Stage(key, 0, false)
                : new DashboardResponse.Stage(key, row.tilesIn() - row.rejects(), row.entries() > 0);
    }

    private static List<DashboardResponse.GradeShare> shares(List<GradeRow> rows, long total) {
        return rows.stream()
                .map(r -> new DashboardResponse.GradeShare(r.code(), r.name(), r.sortOrder(), (int) r.boxes(),
                        scale(r.sqm()), pct(r.boxes(), total)))
                .toList();
    }

    // ------------------------------------------------------------------ finished goods (now)

    private DashboardResponse.FinishedGoods finishedGoods(Long plantId, List<Attention> attention) {
        List<FgStock> stock = stockRepository.findByPlantIdAndBoxesGreaterThan(plantId, 0);
        List<FgStockRow> rows = stock.stream().map(FgStockRow::from).toList();
        int boxes = rows.stream().mapToInt(FgStockRow::boxes).sum();
        BigDecimal sqm = sum(rows.stream().map(FgStockRow::sqm).toList());

        // per grade, best first
        Map<String, List<FgStockRow>> byGrade = rows.stream()
                .sorted(Comparator.comparingInt(FgStockRow::gradeSortOrder))
                .collect(Collectors.groupingBy(FgStockRow::gradeCode, LinkedHashMap::new, Collectors.toList()));
        List<DashboardResponse.GradeShare> grades = byGrade.values().stream().map(list -> {
            FgStockRow first = list.get(0);
            int b = list.stream().mapToInt(FgStockRow::boxes).sum();
            return new DashboardResponse.GradeShare(first.gradeCode(), first.gradeName(), first.gradeSortOrder(), b,
                    scale(sum(list.stream().map(FgStockRow::sqm).toList())), pct(b, boxes));
        }).toList();

        // the designs with the most boxes
        List<DashboardResponse.DesignStock> top = rows.stream()
                .collect(Collectors.groupingBy(FgStockRow::designId)).values().stream()
                .map(list -> new DashboardResponse.DesignStock(list.get(0).designCode(), list.get(0).designName(),
                        list.stream().mapToInt(FgStockRow::boxes).sum(), scale(sum(list.stream().map(FgStockRow::sqm).toList()))))
                .sorted(Comparator.comparingInt(DashboardResponse.DesignStock::boxes).reversed())
                .limit(TOP_DESIGNS)
                .toList();

        // how full the godowns are: pallets standing against the pillars' capacity
        Map<Long, List<FgStockRow>> byPillar = rows.stream().collect(Collectors.groupingBy(FgStockRow::pillarId));
        int pallets = rows.stream().map(FgStockRow::pallets).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        var pillars = queries.pillars(plantId);
        List<String> full = stock.stream()
                .map(FgStock::getPillar).distinct()
                .filter(p -> p.getCapacityPallets() != null)
                .filter(p -> byPillar.getOrDefault(p.getId(), List.of()).stream().map(FgStockRow::pallets)
                        .filter(Objects::nonNull).mapToInt(Integer::intValue).sum() >= p.getCapacityPallets())
                .map(FgPillar::getCode)
                .sorted(FgPillar.CODE_ORDER)
                .toList();
        if (!full.isEmpty()) {
            attention.add(new Attention(Attention.Kind.PILLARS_FULL, String.join(", ", full), full.size(), null));
        }

        int kept = (int) queries.keptByStock(plantId).stream().mapToLong(DashboardQueries.StockKept::kept).sum();
        return new DashboardResponse.FinishedGoods(boxes, scale(sqm), kept, grades, top, pallets,
                pillars.capacity() == null ? null : pillars.capacity().intValue(), byPillar.size(), (int) pillars.pillars());
    }

    // ------------------------------------------------------------------ dispatch

    private DashboardResponse.Dispatch dispatch(Long plantId, List<DispatchRow> dispatched, List<Attention> attention) {
        var lines = queries.waitingLines(plantId);
        Map<Long, Long> kept = queries.keptByLine(plantId).stream()
                .collect(Collectors.toMap(DashboardQueries.LineKept::lineId, DashboardQueries.LineKept::boxes));

        // what confirmed orders still need, per product: production should pack it
        Map<String, Integer> missing = new LinkedHashMap<>();
        Map<String, Set<Long>> missingOrders = new HashMap<>();
        int notPlanned = 0;
        for (var l : lines) {
            int gap = (int) Math.max(0, l.boxes() - kept.getOrDefault(l.lineId(), 0L));
            if (gap > 0) {
                String what = l.designName() + " " + l.gradeName();
                missing.merge(what, gap, Integer::sum);
                missingOrders.computeIfAbsent(what, k -> new HashSet<>()).add(l.orderId());
                notPlanned += gap;
            }
        }
        missing.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> attention.add(new Attention(Attention.Kind.SHORT_FOR_ORDERS, e.getKey(),
                        missingOrders.get(e.getKey()).size(), e.getValue())));

        // stock rows holding fewer boxes than the orders keep from them (broken or moved since planning)
        long tooFew = queries.keptByStock(plantId).stream().filter(s -> s.kept() > s.boxesThere()).count();
        if (tooFew > 0) {
            attention.add(new Attention(Attention.Kind.PLAN_TOO_FEW, null, (int) tooFew, null));
        }

        Set<Long> waitingOrders = lines.stream().map(DashboardQueries.WaitingLine::orderId).collect(Collectors.toSet());
        return new DashboardResponse.Dispatch(
                (int) dispatched.stream().mapToLong(DispatchRow::orders).sum(),
                (int) dispatched.stream().mapToLong(DispatchRow::boxes).sum(),
                scale(sum(dispatched.stream().map(DispatchRow::sqm).toList())),
                waitingOrders.size(), lines.stream().mapToInt(DashboardQueries.WaitingLine::boxes).sum(), notPlanned);
    }

    // ------------------------------------------------------------------ maintenance

    private DashboardResponse.Maintenance maintenance(Long plantId, LocalDate from, LocalDate to, List<Attention> attention) {
        DowntimeResponse d = downtimeService.downtime(plantId, from, to);
        if (!d.downNow().isEmpty()) {
            attention.add(new Attention(Attention.Kind.MACHINES_DOWN,
                    d.downNow().stream().map(x -> x.machine().name()).collect(Collectors.joining(", ")),
                    d.downNow().size(), (int) d.downNow().get(0).minutes()));
        }
        long possible = d.periodMinutes() * Math.max(1, d.machines().size());
        BigDecimal availability = d.machines().isEmpty() ? null
                : BigDecimal.valueOf((possible - d.downtimeMinutes()) * 100).divide(BigDecimal.valueOf(possible), 1, RoundingMode.HALF_UP);
        PmOverview pm = pmTaskService.overview(plantId);
        if (pm.oldest() != null) {
            attention.add(new Attention(Attention.Kind.PM_OVERDUE,
                    pm.oldest().machine().name() + ": " + pm.oldest().title(), pm.overdue(), pm.oldest().daysOverdue()));
        }
        return new DashboardResponse.Maintenance(d.breakdowns(), d.downtimeMinutes(), availability, d.downNow().size(),
                pm.overdue(), pm.dueWeek() + pm.dueToday(), pm.onTimePct());
    }

    // ------------------------------------------------------------------ store

    private DashboardResponse.Store store(Long plantId, List<Attention> attention) {
        List<String> low = queries.lowMaterials(plantId);
        if (!low.isEmpty()) {
            attention.add(new Attention(Attention.Kind.LOW_MATERIALS,
                    String.join(", ", low.subList(0, Math.min(low.size(), 5))) + (low.size() > 5 ? " …" : ""),
                    low.size(), null));
        }
        return new DashboardResponse.Store(queries.storeValue(plantId).setScale(2, RoundingMode.HALF_UP), low.size());
    }

    // ------------------------------------------------------------------ the daily chart

    /** One row per day of the period, zero where nothing happened (a gap in a line chart would hide the zero). */
    private static List<DashboardResponse.Day> days(LocalDate from, LocalDate to,
                                                    List<DashboardQueries.DayRow> packed, List<DispatchRow> dispatched) {
        Map<LocalDate, BigDecimal> p = packed == null ? null : packed.stream()
                .collect(Collectors.toMap(DashboardQueries.DayRow::date, r -> r.sqm() == null ? BigDecimal.ZERO : r.sqm()));
        Map<LocalDate, BigDecimal> d = dispatched == null ? null : dispatched.stream()
                .collect(Collectors.toMap(DispatchRow::date, r -> r.sqm() == null ? BigDecimal.ZERO : r.sqm()));
        List<DashboardResponse.Day> out = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            out.add(new DashboardResponse.Day(day,
                    p == null ? null : scale(p.getOrDefault(day, BigDecimal.ZERO)),
                    d == null ? null : scale(d.getOrDefault(day, BigDecimal.ZERO))));
        }
        return out;
    }

    // ------------------------------------------------------------------ small helpers

    /** Adds up the known values (nulls skipped). */
    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    /** part / whole x 100 with 1 decimal, or null when the whole is 0. */
    private static BigDecimal pct(long part, long whole) {
        return whole == 0 ? null
                : BigDecimal.valueOf(part * 100).divide(BigDecimal.valueOf(whole), 1, RoundingMode.HALF_UP);
    }

    /** amount per m², or null when either is unknown or there were no m². */
    private static BigDecimal per(BigDecimal amount, BigDecimal sqm) {
        return amount == null || sqm == null || sqm.signum() == 0 ? null : amount.divide(sqm, 3, RoundingMode.HALF_UP);
    }
}
