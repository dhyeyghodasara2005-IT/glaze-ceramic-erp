package com.ceramicerp.costing;

import com.ceramicerp.costing.dto.CostSheet;
import com.ceramicerp.costing.dto.DesignCosts;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shares a month's cost sheet out per design, each cost by what shows who used it:
 * <ul>
 *   <li>body material by the tonnes of powder pressed (rejects use powder too: that is the point)</li>
 *   <li>glaze by the grams put on (engobe + glaze grams per tile x tiles), ink by the litres</li>
 *   <li>packing by the boxes</li>
 *   <li>everything else (energy, labour, monthly costs, other material) by the m² packed</li>
 * </ul>
 * Whatever has no usage recorded in the month falls back to m², and the answer says so.
 */
@Service
@RequiredArgsConstructor
public class DesignCostService {

    private final CostSheetService sheetService;
    private final CostQueries queries;

    /** @param month null = this month */
    @Transactional(readOnly = true)
    public DesignCosts designs(Long plantId, YearMonth month) {
        PlantAccess.require(plantId);
        CostSheet sheet = sheetService.sheet(plantId, month);
        LocalDate from = sheet.from();
        LocalDate to = sheet.to();

        Map<Long, Shares> designs = new LinkedHashMap<>();
        Map<Long, BigDecimal> sqm = new HashMap<>();
        Map<Long, BigDecimal> boxes = new HashMap<>();
        Map<Long, BigDecimal> powder = new HashMap<>();
        Map<Long, BigDecimal> grams = new HashMap<>();
        Map<Long, BigDecimal> litres = new HashMap<>();

        for (CostQueries.PackedDesign p : queries.packedByDesign(plantId, from, to)) {
            Shares s = designs.computeIfAbsent(p.designId(), id -> new Shares(id, p.code(), p.name()));
            s.sqm = p.sqm();
            s.boxes = p.boxes();
            sqm.put(p.designId(), p.sqm());
            boxes.put(p.designId(), BigDecimal.valueOf(p.boxes()));
        }
        for (CostQueries.PressedDesign p : queries.pressedByDesign(plantId, from, to)) {
            designs.computeIfAbsent(p.designId(), id -> new Shares(id, p.code(), p.name()));
            powder.put(p.designId(), p.powderTon());
        }
        int withoutUsage = 0;
        for (CostQueries.GlazeEntry e : queries.glazeEntries(plantId, from, to)) {
            designs.computeIfAbsent(e.designId(), id -> new Shares(id, e.code(), e.name()));
            boolean noGrams = e.engobeGpt() == null && e.glazeGpt() == null;
            if (noGrams || e.inkLitres() == null) {
                withoutUsage++;
            }
            BigDecimal perTile = orZero(e.engobeGpt()).add(orZero(e.glazeGpt()));
            grams.merge(e.designId(), perTile.multiply(BigDecimal.valueOf(e.tilesIn())), BigDecimal::add);
            litres.merge(e.designId(), orZero(e.inkLitres()), BigDecimal::add);
        }

        BigDecimal unshared = BigDecimal.ZERO;
        List<String> sharedByArea = new ArrayList<>();
        for (CostSheet.Line line : sheet.lines()) {
            Map<Long, BigDecimal> driver = sqm;
            if (line.section() == CostSheet.Section.MATERIAL) {
                Map<Long, BigDecimal> own = switch (line.code().toUpperCase(Locale.ROOT)) {
                    case "BODY" -> powder;
                    case "GLAZE" -> grams;
                    case "INK" -> litres;
                    case "PACKING" -> boxes;
                    default -> null;
                };
                if (own != null && hasAny(own)) {
                    driver = own;
                } else if (own != null) {
                    sharedByArea.add(line.name());
                }
            }
            Map<Long, BigDecimal> shares = share(line.amount(), driver);
            if (shares.isEmpty()) {
                unshared = unshared.add(line.amount());
            }
            shares.forEach((id, amount) -> designs.get(id).add(line, amount));
        }

        List<DesignCosts.Row> rows = designs.values().stream()
                .map(Shares::row)
                // the dearest first; what wasn't packed yet has no figure, so it goes last
                .sorted(Comparator.comparing(DesignCosts.Row::perSqm, Comparator.nullsFirst(Comparator.<BigDecimal>naturalOrder())).reversed())
                .toList();
        return new DesignCosts(sheet.month(), from, to, sheet.running(), sheet.total(), unshared, sheet.perSqm(), rows,
                sharedByArea, withoutUsage);
    }

    /**
     * An amount divided in proportion. The last one gets what is left, not its own rounded share, so the paise always
     * add up to the amount exactly. Empty when there is nothing to divide by.
     */
    static Map<Long, BigDecimal> share(BigDecimal amount, Map<Long, BigDecimal> driver) {
        BigDecimal base = driver.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<Long, BigDecimal> out = new LinkedHashMap<>();
        if (base.signum() <= 0 || amount.signum() == 0) {
            return out;
        }
        List<Map.Entry<Long, BigDecimal>> using = driver.entrySet().stream().filter(e -> e.getValue().signum() > 0).toList();
        BigDecimal left = amount;
        for (int i = 0; i < using.size(); i++) {
            Map.Entry<Long, BigDecimal> e = using.get(i);
            BigDecimal part = i == using.size() - 1 ? left
                    : amount.multiply(e.getValue()).divide(base, 2, RoundingMode.HALF_UP);
            out.put(e.getKey(), part);
            left = left.subtract(part);
        }
        return out;
    }

    private static boolean hasAny(Map<Long, BigDecimal> driver) {
        return driver.values().stream().anyMatch(v -> v.signum() > 0);
    }

    private static BigDecimal orZero(BigDecimal n) {
        return n == null ? BigDecimal.ZERO : n;
    }

    /** One design while its shares are added up. */
    private static final class Shares {
        final Long id;
        final String code;
        final String name;
        BigDecimal sqm = BigDecimal.ZERO;
        long boxes;
        BigDecimal body = BigDecimal.ZERO;
        BigDecimal glaze = BigDecimal.ZERO;
        BigDecimal ink = BigDecimal.ZERO;
        BigDecimal packing = BigDecimal.ZERO;
        BigDecimal other = BigDecimal.ZERO;
        BigDecimal energy = BigDecimal.ZERO;
        BigDecimal labour = BigDecimal.ZERO;
        BigDecimal monthly = BigDecimal.ZERO;

        Shares(Long id, String code, String name) {
            this.id = id;
            this.code = code;
            this.name = name;
        }

        void add(CostSheet.Line line, BigDecimal amount) {
            switch (line.section()) {
                case ENERGY -> energy = energy.add(amount);
                case LABOUR -> labour = labour.add(amount);
                case MONTHLY -> monthly = monthly.add(amount);
                case MATERIAL -> {
                    switch (line.code().toUpperCase(Locale.ROOT)) {
                        case "BODY" -> body = body.add(amount);
                        case "GLAZE" -> glaze = glaze.add(amount);
                        case "INK" -> ink = ink.add(amount);
                        case "PACKING" -> packing = packing.add(amount);
                        default -> other = other.add(amount);
                    }
                }
            }
        }

        DesignCosts.Row row() {
            BigDecimal total = body.add(glaze).add(ink).add(packing).add(other).add(energy).add(labour).add(monthly);
            return new DesignCosts.Row(id, code, name, sqm, boxes, body, glaze, ink, packing, other, energy, labour, monthly, total,
                    sqm.signum() == 0 ? null : total.divide(sqm, 2, RoundingMode.HALF_UP),
                    boxes == 0 ? null : total.divide(BigDecimal.valueOf(boxes), 2, RoundingMode.HALF_UP));
        }
    }
}
