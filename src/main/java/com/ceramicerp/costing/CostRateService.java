package com.ceramicerp.costing;

import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.costing.dto.CostRateRequest;
import com.ceramicerp.costing.dto.CostRateResponse;
import com.ceramicerp.costing.dto.MonthlyCostRequest;
import com.ceramicerp.costing.dto.MonthlyCostRow;
import com.ceramicerp.energy.MeterKind;
import com.ceramicerp.labour.AttendanceService;
import com.ceramicerp.organization.Plant;
import com.ceramicerp.organization.PlantService;
import com.ceramicerp.security.PlantAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The figures typed in by accounts: fuel rates from a date (a new rate never rewrites the months before it), and
 * the costs that come as one amount a month.
 */
@Service
@RequiredArgsConstructor
public class CostRateService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final CostRateRepository rateRepository;
    private final MonthlyCostRepository monthlyRepository;
    private final PlantService plantService;
    private final AttendanceService attendanceService;

    @Transactional(readOnly = true)
    public List<CostRateResponse> rates(Long plantId) {
        PlantAccess.require(plantId);
        // the kind is stored as text, so the database sorts it A to Z: gas and power first is made here
        return rateRepository.findByPlantIdOrderByKindAscUnitAscValidFromDesc(plantId).stream()
                .sorted(Comparator.comparing(CostRate::getKind))   // stable: within a kind, the newest stays first
                .map(CostRateResponse::from)
                .toList();
    }

    @Transactional
    public CostRateResponse create(CostRateRequest req) {
        Plant plant = plantService.find(req.plantId());
        PlantAccess.require(plant.getId());
        check(req);
        if (rateRepository.existsByPlantIdAndKindAndUnitAndValidFrom(plant.getId(), req.kind(), req.unit(), req.validFrom())) {
            throw taken(req);
        }
        CostRate r = new CostRate();
        r.setPlant(plant);
        apply(r, req);
        return CostRateResponse.from(rateRepository.save(r));
    }

    @Transactional
    public CostRateResponse update(Long id, CostRateRequest req) {
        CostRate r = find(id);
        if (!r.getPlant().getId().equals(req.plantId())) {
            throw new BusinessException("A rate can't be moved to another plant");
        }
        check(req);
        if (rateRepository.existsByPlantIdAndKindAndUnitAndValidFromAndIdNot(r.getPlant().getId(), req.kind(), req.unit(), req.validFrom(), id)) {
            throw taken(req);
        }
        apply(r, req);
        return CostRateResponse.from(r);
    }

    @Transactional
    public void delete(Long id) {
        rateRepository.delete(find(id));
    }

    /** Every cost head of a month, with what was entered and the month before's amount. */
    @Transactional(readOnly = true)
    public List<MonthlyCostRow> monthly(Long plantId, YearMonth month) {
        PlantAccess.require(plantId);
        Map<MonthlyCostHead, MonthlyCost> now = byHead(plantId, month.atDay(1));
        Map<MonthlyCostHead, MonthlyCost> before = byHead(plantId, month.minusMonths(1).atDay(1));
        return Arrays.stream(MonthlyCostHead.values()).map(head -> {
            MonthlyCost c = now.get(head);
            MonthlyCost last = before.get(head);
            return new MonthlyCostRow(head, c == null ? null : c.getAmount(), c == null ? null : c.getNote(),
                    c == null ? null : c.getUpdatedBy(), last == null ? null : last.getAmount());
        }).toList();
    }

    @Transactional
    public List<MonthlyCostRow> saveMonthly(MonthlyCostRequest req) {
        Plant plant = plantService.find(req.plantId());
        PlantAccess.require(plant.getId());
        if (req.month().atDay(1).isAfter(attendanceService.today())) {
            throw new BusinessException("That month hasn't started");
        }
        Set<MonthlyCostHead> seen = new HashSet<>();
        LocalDate start = req.month().atDay(1);
        Map<MonthlyCostHead, MonthlyCost> saved = byHead(plant.getId(), start);
        for (MonthlyCostRequest.Line line : req.lines()) {
            if (!seen.add(line.head())) {
                throw new BusinessException("A cost head is in the list twice");
            }
            MonthlyCost c = saved.get(line.head());
            if (line.amount() == null) {
                if (c != null) {
                    monthlyRepository.delete(c);
                }
                continue;
            }
            if (c == null) {
                c = new MonthlyCost();
                c.setPlant(plant);
                c.setMonthStart(start);
                c.setHead(line.head());
            }
            c.setAmount(line.amount());
            c.setNote(Text.trimToNull(line.note()));
            monthlyRepository.save(c);
        }
        monthlyRepository.flush();
        return monthly(plant.getId(), req.month());
    }

    private Map<MonthlyCostHead, MonthlyCost> byHead(Long plantId, LocalDate start) {
        return monthlyRepository.findByPlantIdAndMonthStart(plantId, start).stream()
                .collect(Collectors.toMap(MonthlyCost::getHead, Function.identity()));
    }

    private CostRate find(Long id) {
        CostRate r = rateRepository.findWithPlantById(id).orElseThrow(() -> new NotFoundException("Rate", id));
        PlantAccess.require(r.getPlant().getId());
        return r;
    }

    private static void check(CostRateRequest req) {
        if (req.kind() == MeterKind.RUN_HOURS) {
            throw new BusinessException("Running hours have no rate of their own: give the diesel a rate");
        }
        if (!req.kind().getUnits().contains(req.unit())) {
            throw new BusinessException("A " + name(req.kind()) + " rate is per "
                    + String.join(" or ", req.kind().getUnits().stream().map(Enum::name).toList()));
        }
    }

    private static BusinessException taken(CostRateRequest req) {
        return new BusinessException("There is already a " + name(req.kind()) + " rate from " + DATE.format(req.validFrom()));
    }

    private static String name(MeterKind kind) {
        return kind.name().toLowerCase(Locale.ROOT);
    }

    private static void apply(CostRate r, CostRateRequest req) {
        r.setKind(req.kind());
        r.setUnit(req.unit());
        r.setValidFrom(req.validFrom());
        r.setRate(req.rate());
        r.setNote(Text.trimToNull(req.note()));
    }
}
