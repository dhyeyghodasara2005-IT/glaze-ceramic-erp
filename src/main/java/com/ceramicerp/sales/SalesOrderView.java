package com.ceramicerp.sales;

import com.ceramicerp.fg.FgPillar;
import com.ceramicerp.fg.FgStock;
import com.ceramicerp.fg.FgStockRepository;
import com.ceramicerp.fg.dto.DesignGradeStock;
import com.ceramicerp.fg.dto.FgStockRow;
import com.ceramicerp.master.Design;
import com.ceramicerp.master.Grade;
import com.ceramicerp.master.TileSize;
import com.ceramicerp.party.Party;
import com.ceramicerp.sales.dto.SalesOption;
import com.ceramicerp.sales.dto.SalesOrderResponse;
import com.ceramicerp.sales.dto.SalesParty;
import com.ceramicerp.sales.dto.StockKept;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the answer for one order: its lines with the stock checks, its loading plan and its totals. Shared by
 * SalesOrderService and LoadingService; runs inside their transaction.
 */
@Component
@RequiredArgsConstructor
public class SalesOrderView {

    /** Glaze's weight may differ this much from the slip's before it is flagged (2%): box weights differ a little. */
    private static final BigDecimal WEIGHT_TOLERANCE = new BigDecimal("0.02");

    /** The forklift's order through the godown: walk order (nearest first), then the pillar code. */
    private static final Comparator<SalesOrderResponse.Pick> WALK = Comparator
            .comparing(SalesOrderResponse.Pick::godownCode)
            .thenComparing(SalesOrderResponse.Pick::walkOrder, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(SalesOrderResponse.Pick::pillarCode, FgPillar.CODE_ORDER)
            .thenComparing(SalesOrderResponse.Pick::lineNo);

    private final FgStockRepository stockRepository;
    private final SalesOrderPickRepository pickRepository;
    private final ProductAliasRepository aliasRepository;

    private record DesignGrade(Long designId, Long gradeId) {
    }

    /** Needs the plant, party and transporter loaded; lines, picks, designs and grades are loaded as needed. */
    public SalesOrderResponse of(SalesOrder o) {
        List<SalesOrderLine> lines = o.getLines();
        // stock is only checked while the boxes are still in the godown for this order
        boolean checkStock = o.getStatus() == SalesOrderStatus.DRAFT || o.getStatus() == SalesOrderStatus.CONFIRMED;

        // Boxes in stock, and boxes other confirmed orders keep, per design + grade: two queries for all lines.
        Map<DesignGrade, Long> stock = new HashMap<>();
        Map<DesignGrade, Long> keptElsewhere = new HashMap<>();
        List<Long> designIds = lines.stream().map(SalesOrderLine::getDesign).filter(Objects::nonNull)
                .map(Design::getId).distinct().toList();
        if (checkStock && !designIds.isEmpty()) {
            Long plantId = o.getPlant().getId();
            for (DesignGradeStock s : stockRepository.totalsByDesignAndGrade(plantId, designIds)) {
                stock.put(new DesignGrade(s.designId(), s.gradeId()), s.boxes());
            }
            // a new order has no id yet: nothing of its own to leave out
            Long self = o.getId() == null ? -1L : o.getId();
            for (DesignGradeStock s : pickRepository.keptByDesignAndGrade(plantId, designIds, self)) {
                keptElsewhere.put(new DesignGrade(s.designId(), s.gradeId()), s.boxes());
            }
        }
        Map<String, ProductAlias> aliases = aliasRepository.findByNameKeyIn(lines.stream()
                        .map(l -> ProductMatcher.key(l.getDescription())).distinct().toList()).stream()
                .collect(Collectors.toMap(ProductAlias::getNameKey, Function.identity()));
        boolean planned = o.getStatus() != SalesOrderStatus.DRAFT;
        Map<Long, List<SalesOrderPick>> picksByLine = o.getPicks().stream()
                .collect(Collectors.groupingBy(p -> p.getLine().getId()));

        // Two lines of the same design and grade share its free boxes: the first line is served first.
        Map<DesignGrade, Long> left = new HashMap<>();
        stock.forEach((dg, boxes) -> left.put(dg, boxes - keptElsewhere.getOrDefault(dg, 0L)));
        List<SalesOrderResponse.Line> out = new ArrayList<>();
        for (SalesOrderLine l : lines) {
            Design design = l.getDesign();
            Grade grade = l.getGrade();
            TileSize size = design == null ? null : design.getTileSize();
            Long inStock = null;
            Long kept = null;
            Integer shortBy = null;
            if (design != null && grade != null && checkStock) {
                DesignGrade dg = new DesignGrade(design.getId(), grade.getId());
                inStock = stock.getOrDefault(dg, 0L);
                kept = keptElsewhere.getOrDefault(dg, 0L);
                long available = Math.max(0, left.getOrDefault(dg, 0L));
                shortBy = (int) Math.max(0, l.getBoxes() - available);
                left.put(dg, Math.max(0, available - l.getBoxes()));
            }
            List<SalesOrderPick> linePicks = picksByLine.getOrDefault(l.getId(), List.of());
            boolean anyTicked = linePicks.stream().anyMatch(p -> p.getLoadedBoxes() != null);
            ProductAlias alias = aliases.get(ProductMatcher.key(l.getDescription()));
            boolean remembered = alias != null && design != null && grade != null
                    && alias.getDesign().getId().equals(design.getId()) && alias.getGrade().getId().equals(grade.getId());
            out.add(new SalesOrderResponse.Line(l.getId(), l.getLineNo(), l.getDescription(),
                    design == null ? null : ProductMatcher.option(design),
                    grade == null ? null : ProductMatcher.option(grade), l.getBoxes(), l.getMrpBox(), l.getMrpPiece(),
                    l.getBatchRef(), size == null ? null : FgStockRow.sqm(l.getBoxes(), size), weight(l.getBoxes(), size),
                    inStock, kept, shortBy,
                    planned ? linePicks.stream().mapToInt(SalesOrderPick::getPlannedBoxes).sum() : null,
                    anyTicked ? linePicks.stream().map(SalesOrderPick::getLoadedBoxes).filter(Objects::nonNull)
                            .mapToInt(Integer::intValue).sum() : null,
                    remembered));
        }

        List<SalesOrderResponse.Pick> picks = picks(o);
        Party t = o.getTransporter();
        return new SalesOrderResponse(o.getId(), o.getPlant().getId(), o.getPlant().getCode(), o.getSlipNo(),
                o.getSlipDate(), o.getOrderRef(), o.getOrderDate(), o.getOrderType(), o.getStatus(), o.getSource(),
                SalesParty.from(o.getParty()),
                t == null ? null : new SalesOption(t.getId(), t.getCode(), t.getName(), t.getCity()),
                o.getTruckNo(), o.getDriverName(), o.getDriverMobile(), o.getLrNo(),
                o.getSlipBoxes(), o.getSlipWeightKg(), o.getRemarks(), out, picks, totals(o, out, picks),
                o.getConfirmedAt(), o.getConfirmedBy(), o.getPlannedAt(), o.getDispatchNo(), o.getDispatchDate(),
                o.getDispatchedAt(), o.getDispatchedBy(), o.getUndoneAt(), o.getUndoneBy(), o.getUndoReason(),
                o.getCancelledAt(), o.getCancelledBy(), o.getCancelReason(), o.getCreatedAt(), o.getCreatedBy(),
                o.getVersion());
    }

    /** The loading plan in walk order, each row flagged when its stock row no longer holds what is kept from it. */
    private List<SalesOrderResponse.Pick> picks(SalesOrder o) {
        if (o.getPicks().isEmpty()) {
            return List.of();
        }
        Map<Long, Long> keptByOthers = new HashMap<>();
        if (o.getStatus() == SalesOrderStatus.CONFIRMED) {
            List<Long> stockIds = o.getPicks().stream().map(p -> p.getStock().getId()).distinct().toList();
            for (StockKept k : pickRepository.keptByStock(stockIds, o.getId())) {
                keptByOthers.put(k.stockId(), k.boxes());
            }
        }
        return o.getPicks().stream().map(p -> {
            FgStock s = p.getStock();
            FgPillar pillar = s.getPillar();
            boolean tooFew = o.getStatus() == SalesOrderStatus.CONFIRMED
                    && s.getBoxes() < p.keptBoxes() + keptByOthers.getOrDefault(s.getId(), 0L);
            return new SalesOrderResponse.Pick(p.getId(), p.getLine().getId(), p.getLine().getLineNo(), s.getId(),
                    pillar.getGodown().getCode(), pillar.getCode(), pillar.getZone(), pillar.getWalkOrder(),
                    pillar.getKind(), s.getDesign().getName(), s.getGrade().getName(), s.getBatchCode(), s.getShade(),
                    s.getCaliber(), p.getPlannedBoxes(), p.getLoadedBoxes(), p.getReason(),
                    s.getDesign().getTileSize().getBoxesPerPallet(), s.getBoxes(), tooFew, p.getLoadedAt(),
                    p.getLoadedBy());
        }).sorted(WALK).toList();
    }

    /** Glaze's own totals, how far they are from what the slip prints, and how far the loading is. */
    private static SalesOrderResponse.Totals totals(SalesOrder o, List<SalesOrderResponse.Line> lines,
                                                    List<SalesOrderResponse.Pick> picks) {
        int boxes = lines.stream().mapToInt(SalesOrderResponse.Line::boxes).sum();
        // a total is only shown when every line has its part (a partial total would look like a wrong slip)
        BigDecimal sqm = sumOrNull(lines.stream().map(SalesOrderResponse.Line::sqm).toList());
        BigDecimal weight = sumOrNull(lines.stream().map(SalesOrderResponse.Line::weightKg).toList());
        Integer boxesDiff = o.getSlipBoxes() == null ? null : boxes - o.getSlipBoxes();
        BigDecimal weightDiff = weight == null || o.getSlipWeightKg() == null ? null
                : weight.subtract(o.getSlipWeightKg());
        Boolean weightOk = weightDiff == null ? null
                : weightDiff.abs().compareTo(o.getSlipWeightKg().multiply(WEIGHT_TOLERANCE)) <= 0;
        int unmatched = (int) lines.stream().filter(l -> l.design() == null || l.grade() == null).count();
        int shortLines = (int) lines.stream().filter(l -> l.shortBy() != null && l.shortBy() > 0).count();
        int planned = picks.stream().mapToInt(SalesOrderResponse.Pick::plannedBoxes).sum();
        int loaded = picks.stream().map(SalesOrderResponse.Pick::loadedBoxes).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).sum();
        int toTick = (int) picks.stream().filter(p -> p.loadedBoxes() == null).count();
        return new SalesOrderResponse.Totals(boxes, sqm, weight, boxesDiff, weightDiff, weightOk, unmatched, shortLines,
                planned, loaded, toTick);
    }

    /** boxes x box weight of the size, or null when the size has no box weight. */
    static BigDecimal weight(int boxes, TileSize size) {
        return size == null || size.getBoxWeightKg() == null ? null
                : size.getBoxWeightKg().multiply(BigDecimal.valueOf(boxes)).setScale(1, RoundingMode.HALF_UP);
    }

    private static BigDecimal sumOrNull(List<BigDecimal> values) {
        if (values.isEmpty() || values.stream().anyMatch(Objects::isNull)) {
            return null;
        }
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
