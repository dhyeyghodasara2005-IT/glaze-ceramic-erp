package com.ceramicerp.production;

import com.ceramicerp.master.MachineType;
import com.ceramicerp.master.PlantLayout;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which press entries are behind which design lots. The press makes green tiles with no design, so nothing on a
 * press entry names a lot. The link is the shift:
 * <pre>
 *   PRESS-1, 4 Nov, shift A  -- pressed 1,450 tiles from SILO-2
 *   DP-1,    4 Nov, shift A  -- glazed 1,410 tiles of lot LOT/0027 (Travertino)
 *        => LOT/0027's powder came from SILO-2
 * </pre>
 * On a plant with two lines, only the presses of the lot's own line count: the ones up the layout from its printer
 * (V25). With no layout, every press of the plant does. This is a shift-level match, not a tile-by-tile one: when a
 * design changes in the middle of a shift, both lots of that shift share its press entries.
 */
@Component
@RequiredArgsConstructor
class LotPressMatch {

    private final PressEntryRepository pressRepository;
    private final LineEntryRepository lineRepository;
    private final PlantLayout layout;

    /** The presses on a printer's line, or empty = every press (no layout). */
    Set<Long> pressesFeeding(Long plantId, Long printerId) {
        return layout.upstream(plantId, printerId, MachineType.PRESS);
    }

    /** The posted press entries behind one lot, oldest first. */
    List<PressEntry> behind(ProductionLot lot) {
        Long plantId = lot.getPlant().getId();
        Set<Long> presses = pressesFeeding(plantId, lot.getPrinter().getId());
        // "in ()" with an empty list is not valid SQL, so an unused id stands in when every press counts.
        return pressRepository.findBehindLot(plantId, lot.getId(), presses.isEmpty(), presses.isEmpty() ? List.of(-1L) : presses);
    }

    /** The reverse: the lots these (posted) press entries fed, newest lot first. */
    List<ProductionLot> fedBy(Collection<PressEntry> entries) {
        if (entries.isEmpty()) {
            return List.of();
        }
        Map<Long, ProductionLot> lots = new LinkedHashMap<>();
        Map<Long, Set<Long>> linePresses = new HashMap<>();   // printer -> its presses, worked out once
        Map<Long, List<PressEntry>> byPlant = new HashMap<>();
        entries.forEach(e -> byPlant.computeIfAbsent(e.getPlant().getId(), k -> new ArrayList<>()).add(e));

        byPlant.forEach((plantId, list) -> {
            LocalDate from = list.stream().map(PressEntry::getProdDate).min(Comparator.naturalOrder()).orElseThrow();
            LocalDate to = list.stream().map(PressEntry::getProdDate).max(Comparator.naturalOrder()).orElseThrow();
            for (LineEntry g : lineRepository.findPostedGlazeBetween(plantId, from, to)) {
                ProductionLot lot = g.getLot();
                Set<Long> presses = linePresses.computeIfAbsent(lot.getPrinter().getId(), p -> pressesFeeding(plantId, p));
                boolean fed = list.stream().anyMatch(e -> e.getProdDate().equals(g.getProdDate())
                        && e.getShift().getId().equals(g.getShift().getId())
                        && (presses.isEmpty() || presses.contains(e.getPress().getId())));
                if (fed) {
                    lots.putIfAbsent(lot.getId(), lot);
                }
            }
        });
        return lots.values().stream().sorted(Comparator.comparing(ProductionLot::getId).reversed()).toList();
    }
}
