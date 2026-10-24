package com.ceramicerp.sales;

import com.ceramicerp.fg.FgMoveRepository;
import com.ceramicerp.fg.FgPillar;
import com.ceramicerp.fg.FgStock;
import com.ceramicerp.fg.FgStockRepository;
import com.ceramicerp.fg.dto.StockArrival;
import com.ceramicerp.sales.dto.StockKept;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The loading plan: which boxes each line of a confirmed order takes. Any free box of the line's design and grade
 * may be taken, whatever its batch, shade or caliber (one product's tiles match side by side). The rules, in order:
 * <ol>
 *   <li>pallets in front of a pillar before pallets behind newer ones (no digging);</li>
 *   <li>taking everything a pillar holds (the pillar becomes free), then everything in a stock row;</li>
 *   <li>the oldest boxes first (first in, first out);</li>
 *   <li>the stock row with the most boxes (fewer pillars for the line);</li>
 *   <li>the pillar nearest to the loading point.</li>
 * </ol>
 * The picks then show in walk order, so the forklift passes through the godown once.
 */
@Service
@RequiredArgsConstructor
public class LoadingPlanner {

    private final FgStockRepository stockRepository;
    private final FgMoveRepository moveRepository;
    private final SalesOrderPickRepository pickRepository;
    private final PlanLock planLock;

    /** One stock row as the rules see it. {@code free} goes down as lines take from it. */
    private static final class Candidate {
        final FgStock stock;
        final Instant firstIn;
        final boolean behind;
        final boolean onlyInPillar;
        int free;

        Candidate(FgStock stock, int free, Instant firstIn, boolean behind, boolean onlyInPillar) {
            this.stock = stock;
            this.free = free;
            this.firstIn = firstIn;
            this.behind = behind;
            this.onlyInPillar = onlyInPillar;
        }
    }

    private record DesignGrade(Long designId, Long gradeId) {
    }

    /**
     * Picks the boxes for every line of a confirmed order, replacing any earlier plan. A line that can't be filled
     * gets what there is; the order shows the rest as not planned.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void plan(SalesOrder order) {
        Long plantId = order.getPlant().getId();
        planLock.lock(plantId);

        order.getPicks().clear();
        // delete the old picks before the new ones go in: (line, stock) is unique (see PackEntryService)
        pickRepository.flush();

        Map<DesignGrade, List<Candidate>> candidates = candidates(order);
        for (SalesOrderLine line : order.getLines()) {
            List<Candidate> rows = candidates.getOrDefault(
                    new DesignGrade(line.getDesign().getId(), line.getGrade().getId()), List.of());
            int remaining = line.getBoxes();
            while (remaining > 0) {
                int need = remaining;
                List<Candidate> open = rows.stream()
                        .filter(c -> c.free > 0)
                        .sorted(order(need))
                        .toList();
                if (open.isEmpty()) {
                    break;                  // short: the rest stays "not planned"
                }
                Candidate best = open.get(0);
                int take = Math.min(best.free, remaining);

                SalesOrderPick pick = new SalesOrderPick();
                pick.setOrder(order);
                pick.setLine(line);
                pick.setStock(best.stock);
                pick.setPlannedBoxes(take);
                pick.setReason(reason(best, open.size() > 1 ? open.get(1) : null, need));
                order.getPicks().add(pick);

                best.free -= take;
                remaining -= take;
            }
        }
        order.setPlannedAt(Instant.now());
    }

    /** Every stock row the order's lines could take from, with its free boxes and its place in the pillar. */
    private Map<DesignGrade, List<Candidate>> candidates(SalesOrder order) {
        List<Long> designIds = order.getLines().stream().map(l -> l.getDesign().getId()).distinct().toList();
        List<FgStock> rows = stockRepository.findByPlantIdAndDesignIdInAndBoxesGreaterThan(
                order.getPlant().getId(), designIds, 0);
        if (rows.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> keptElsewhere = pickRepository.keptByStock(ids(rows), order.getId()).stream()
                .collect(Collectors.toMap(StockKept::stockId, StockKept::boxes));

        // Everything standing at those pillars (other designs too), and when each row last got boxes:
        // the newest pallets stand in front.
        Set<Long> pillarIds = rows.stream().map(r -> r.getPillar().getId()).collect(Collectors.toSet());
        List<FgStock> atPillars = stockRepository.findByPillarIdInAndBoxesGreaterThan(pillarIds, 0);
        Map<Long, StockArrival> arrivals = moveRepository.arrivals(ids(atPillars)).stream()
                .collect(Collectors.toMap(StockArrival::stockId, Function.identity()));
        Map<Long, List<FgStock>> byPillar = atPillars.stream().collect(Collectors.groupingBy(s -> s.getPillar().getId()));

        Map<DesignGrade, List<Candidate>> result = new HashMap<>();
        for (FgStock row : rows) {
            int free = (int) (row.getBoxes() - keptElsewhere.getOrDefault(row.getId(), 0L));
            if (free <= 0) {
                continue;
            }
            List<FgStock> neighbours = byPillar.getOrDefault(row.getPillar().getId(), List.of());
            Instant lastIn = lastIn(arrivals, row);
            boolean behind = neighbours.stream()
                    .anyMatch(n -> !n.getId().equals(row.getId()) && isAfter(lastIn(arrivals, n), lastIn));
            boolean onlyInPillar = neighbours.stream().allMatch(n -> n.getId().equals(row.getId()));
            StockArrival a = arrivals.get(row.getId());
            result.computeIfAbsent(new DesignGrade(row.getDesign().getId(), row.getGrade().getId()), k -> new ArrayList<>())
                    .add(new Candidate(row, free, a == null ? null : a.firstIn(), behind, onlyInPillar));
        }
        return result;
    }

    // ------------------------------------------------------------------ the rules

    /** Best first, for a line that still needs {@code need} boxes. */
    private static Comparator<Candidate> order(int need) {
        return Comparator.<Candidate>comparingInt(c -> c.behind ? 1 : 0)
                .thenComparingInt(c -> clears(c, need))
                .thenComparing(c -> c.firstIn, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(c -> -c.free)
                .thenComparing(c -> c.stock.getPillar().getWalkOrder(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(c -> c.stock.getPillar().getCode(), FgPillar.CODE_ORDER);
    }

    /** 0 = takes everything at the pillar, 1 = takes everything free in this stock row, 2 = takes part of it. */
    private static int clears(Candidate c, int need) {
        if (c.free > need) {
            return 2;
        }
        return c.onlyInPillar && c.free == c.stock.getBoxes() ? 0 : 1;
    }

    /** The first rule that put the chosen row ahead of the next best one. */
    private static PickReason reason(Candidate best, Candidate next, int need) {
        if (next == null) {
            return PickReason.ONLY_STOCK;
        }
        if (!best.behind && next.behind) {
            return PickReason.IN_FRONT;
        }
        int b = clears(best, need);
        if (b < clears(next, need)) {
            return b == 0 ? PickReason.CLEARS_PILLAR : PickReason.WHOLE_STOCK;
        }
        if (best.firstIn != null && (next.firstIn == null || best.firstIn.isBefore(next.firstIn))) {
            return PickReason.OLDEST;
        }
        if (best.free > next.free) {
            return PickReason.MOST_BOXES;
        }
        return PickReason.NEAREST;
    }

    // ------------------------------------------------------------------ small helpers

    private static Instant lastIn(Map<Long, StockArrival> arrivals, FgStock s) {
        StockArrival a = arrivals.get(s.getId());
        return a == null ? null : a.lastIn();
    }

    private static boolean isAfter(Instant a, Instant b) {
        return a != null && (b == null || a.isAfter(b));
    }

    private static List<Long> ids(List<FgStock> rows) {
        return rows.stream().map(FgStock::getId).filter(Objects::nonNull).distinct().toList();
    }
}
