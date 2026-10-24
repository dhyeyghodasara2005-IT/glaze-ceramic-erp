package com.ceramicerp.fg;

import com.ceramicerp.fg.dto.FgStockRow;
import com.ceramicerp.fg.dto.PutAwaySuggestion;
import com.ceramicerp.fg.dto.PutAwaySuggestion.Reason;
import com.ceramicerp.master.Design;
import com.ceramicerp.security.PlantAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Where should new pallets go? Suggests pillars, best first, following the godown rules:
 * <ol>
 *   <li>SAME_BOX: on top of the same box (batch, grade, shade, caliber) where there is room - one kind per pillar;</li>
 *   <li>REMAINDER: less than a pallet goes to a remainder pillar near the loading point;</li>
 *   <li>NEXT_TO_DESIGN: an empty pillar next to this design's other pillars, so a design isn't scattered;</li>
 *   <li>EMPTY_IN_ZONE: an empty pillar in the right zone (best sellers next to the loading point), nearest first;</li>
 *   <li>EMPTY_ELSEWHERE: any other empty pillar, keeping the fast zone for best sellers as long as possible.</li>
 * </ol>
 * Pillars holding other boxes are never suggested (mixing is the user's choice, and the screen warns).
 */
@Service
@RequiredArgsConstructor
public class PutAwayService {

    /** Enough choices to pick from without scrolling. */
    private static final int MAX_SUGGESTIONS = 5;
    /** "Next to" = up to this many pillar numbers away in the same row. */
    private static final int NEIGHBOUR_DISTANCE = 2;

    private final FgPillarRepository pillarRepository;
    private final FgStockRepository stockRepository;
    private final FgDesignSettingRepository settingRepository;

    /** The boxes to put away. boxes may be empty (not typed yet): then capacity is not checked. */
    public record Request(Long plantId, Design design, String batchCode, Long gradeId, String shade, String caliber,
                          Integer boxes) {
    }

    /** One pillar as the rules see it. */
    private record Spot(FgPillar pillar, List<FgStockRow> rows, Integer freePallets) {

        boolean empty() {
            return rows.isEmpty();
        }

        boolean fits(Integer needed) {
            if (freePallets == null) {
                return true;            // capacity or pallet size not known: we can't say it's full
            }
            return needed == null ? freePallets > 0 : freePallets >= needed;
        }
    }

    @Transactional(readOnly = true)
    public List<PutAwaySuggestion> suggest(Request r) {
        PlantAccess.require(r.plantId());
        Integer perPallet = r.design().getTileSize().getBoxesPerPallet();
        Integer needed = r.boxes() == null || perPallet == null || perPallet < 1 ? null : (r.boxes() + perPallet - 1) / perPallet;
        boolean lessThanAPallet = r.boxes() != null && perPallet != null && r.boxes() < perPallet;
        boolean fast = settingRepository.findByDesignId(r.design().getId()).map(FgDesignSetting::isFastMover).orElse(false);
        PillarZone home = fast ? PillarZone.FAST : PillarZone.NORMAL;

        List<Spot> spots = spots(r.plantId());

        // Rows of this design (any batch), to find its neighbourhood.
        List<FgPillar> designPillars = spots.stream()
                .filter(s -> s.rows().stream().anyMatch(row -> row.designId().equals(r.design().getId())))
                .map(Spot::pillar).toList();

        Map<Long, PutAwaySuggestion> picked = new LinkedHashMap<>();
        // 1. the same box, with room
        add(picked, spots, Reason.SAME_BOX, s -> s.pillar().getKind() == PillarKind.STORAGE && !s.empty() && s.fits(needed)
                && s.rows().stream().allMatch(row -> sameBox(row, r)), byWalk());
        // 2. a small leftover to a remainder pillar
        if (lessThanAPallet) {
            add(picked, spots, Reason.REMAINDER, s -> s.pillar().getKind() == PillarKind.REMAINDER && s.fits(needed), byWalk());
        }
        // 3. an empty pillar next to the design's other pillars
        add(picked, spots, Reason.NEXT_TO_DESIGN, s -> emptyStorage(s, needed) && distanceTo(s.pillar(), designPillars) <= NEIGHBOUR_DISTANCE,
                Comparator.comparingInt((Spot s) -> distanceTo(s.pillar(), designPillars)).thenComparing(byWalk()));
        // 4. an empty pillar in the design's zone, nearest the loading point first
        add(picked, spots, Reason.EMPTY_IN_ZONE, s -> emptyStorage(s, needed) && s.pillar().getZone() == home, byWalk());
        // 5. any other empty pillar; the fast zone last unless this is a best seller
        add(picked, spots, Reason.EMPTY_ELSEWHERE, s -> emptyStorage(s, needed),
                Comparator.comparingInt((Spot s) -> zonePreference(s.pillar().getZone(), fast)).thenComparing(byWalk()));

        return List.copyOf(picked.values());
    }

    /** Open pillars of open godowns (not staging lanes), with what stands there and the room left. */
    private List<Spot> spots(Long plantId) {
        Map<Long, List<FgStockRow>> byPillar = new HashMap<>();
        for (FgStock s : stockRepository.findByPlantIdAndBoxesGreaterThan(plantId, 0)) {
            byPillar.computeIfAbsent(s.getPillar().getId(), k -> new ArrayList<>()).add(FgStockRow.from(s));
        }
        List<Spot> spots = new ArrayList<>();
        for (FgPillar p : pillarRepository.findByGodownPlantId(plantId)) {
            if (!p.isActive() || !p.getGodown().isActive() || p.getKind() == PillarKind.STAGING) {
                continue;
            }
            List<FgStockRow> rows = byPillar.getOrDefault(p.getId(), List.of());
            Integer free = p.getCapacityPallets() == null || FgGodownService.palletsUnknown(rows) ? null
                    : p.getCapacityPallets() - FgGodownService.pallets(rows);
            spots.add(new Spot(p, rows, free));
        }
        return spots;
    }

    private static void add(Map<Long, PutAwaySuggestion> picked, List<Spot> spots, Reason reason, Predicate<Spot> rule,
                            Comparator<Spot> order) {
        spots.stream()
                .filter(rule)
                .filter(s -> !picked.containsKey(s.pillar().getId()))
                .sorted(order)
                .limit(Math.max(0, MAX_SUGGESTIONS - picked.size()))
                .forEach(s -> picked.put(s.pillar().getId(), new PutAwaySuggestion(s.pillar().getId(), s.pillar().getCode(),
                        s.pillar().getGodown().getCode(), s.pillar().getZone(), s.pillar().getWalkOrder(), reason, s.freePallets())));
    }

    private static boolean emptyStorage(Spot s, Integer needed) {
        return s.pillar().getKind() == PillarKind.STORAGE && s.empty() && s.fits(needed);
    }

    private static boolean sameBox(FgStockRow row, Request r) {
        return row.designId().equals(r.design().getId()) && row.batchCode().equals(r.batchCode())
                && row.gradeId().equals(r.gradeId()) && Objects.equals(row.shade(), r.shade())
                && Objects.equals(row.caliber(), r.caliber());
    }

    /** Nearest the loading point first; pillars without a walk order last; then by code (A2 before A10). */
    private static Comparator<Spot> byWalk() {
        return Comparator.comparing((Spot s) -> s.pillar().getWalkOrder(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(s -> s.pillar().getGodown().getCode())
                .thenComparing(s -> s.pillar().getCode(), FgPillar.CODE_ORDER);
    }

    /** How many pillar numbers away from the nearest pillar of the design in the same row (same godown). */
    private static int distanceTo(FgPillar pillar, List<FgPillar> designPillars) {
        int best = Integer.MAX_VALUE;
        String row = FgPillar.rowOf(pillar.getCode());
        int n = FgPillar.numberOf(pillar.getCode());
        for (FgPillar other : designPillars) {
            if (other.getGodown().getId().equals(pillar.getGodown().getId()) && FgPillar.rowOf(other.getCode()).equals(row)
                    && n >= 0 && FgPillar.numberOf(other.getCode()) >= 0) {
                best = Math.min(best, Math.abs(FgPillar.numberOf(other.getCode()) - n));
            }
        }
        return best;
    }

    /** Best sellers: FAST, NORMAL, SLOW. Others: NORMAL, SLOW, and FAST last (kept free for best sellers). */
    private static int zonePreference(PillarZone zone, boolean fast) {
        if (fast) {
            return zone.ordinal();
        }
        return switch (zone) {
            case NORMAL -> 0;
            case SLOW -> 1;
            case FAST -> 2;
        };
    }
}
