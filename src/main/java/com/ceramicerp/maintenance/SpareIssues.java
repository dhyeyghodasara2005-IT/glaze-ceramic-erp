package com.ceramicerp.maintenance;

import com.ceramicerp.maintenance.dto.SpareLine;
import com.ceramicerp.maintenance.dto.SpareUsed;
import com.ceramicerp.master.Material;
import com.ceramicerp.store.StoreDocType;
import com.ceramicerp.store.StoreDocument;
import com.ceramicerp.store.StoreDocumentLine;
import com.ceramicerp.store.StoreDocumentService;
import com.ceramicerp.store.StoreLocation;
import com.ceramicerp.store.dto.StoreDocumentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Spares used by maintenance leave the store through a store issue made on maintenance's behalf (breakdowns since
 * 8a, preventive jobs since 8b). The store checks the materials, the godowns and the stock, and values them at
 * average cost; the origin marks the issue so the store screens can't change it on their own.
 */
@Component
@RequiredArgsConstructor
class SpareIssues {

    private final StoreDocumentService storeDocumentService;

    /** A spare as kept on the breakdown or job: what, from where, how much. */
    record Taken(Material material, StoreLocation location, BigDecimal quantity) {
    }

    /** Posts the issue (null when nothing was used) and returns it with what was taken, line by line. */
    StoreDocument issue(String origin, Long plantId, Long machineId, LocalDate date, String reference, List<SpareLine> spares) {
        if (spares == null || spares.isEmpty()) {
            return null;
        }
        List<StoreDocumentRequest.Line> lines = spares.stream()
                .map(s -> new StoreDocumentRequest.Line(s.materialId(), s.locationId(), s.quantity(), null, null, null, null, null))
                .toList();
        return storeDocumentService.postIssueFor(origin, new StoreDocumentRequest(plantId, StoreDocType.ISSUE, date,
                null, null, null, null, null, machineId, reference, null, "Spares for " + reference, lines));
    }

    /** What the posted issue took, in the same order as its lines. */
    static List<Taken> taken(StoreDocument issue) {
        return issue == null ? List.of()
                : issue.getLines().stream().map(l -> new Taken(l.getMaterial(), l.getLocation(), l.getQuantity())).toList();
    }

    /** Puts the spares back (cancelling a breakdown or a job). */
    void undo(StoreDocument issue, String why) {
        if (issue != null) {
            storeDocumentService.cancelFor(issue, why);
        }
    }

    /** The spares as the screens show them, valued from the issue's lines (same order). */
    static List<SpareUsed> view(List<Taken> spares, StoreDocument issue) {
        List<StoreDocumentLine> issued = issue == null ? List.of() : issue.getLines();
        List<SpareUsed> out = new ArrayList<>();
        for (int i = 0; i < spares.size(); i++) {
            Taken s = spares.get(i);
            BigDecimal value = i < issued.size() ? issued.get(i).getValue() : null;
            out.add(new SpareUsed(i + 1, s.material().getId(), s.material().getCode(), s.material().getName(),
                    s.material().getUnit().getCode(), s.location().getId(), s.location().getCode(), s.quantity(), value));
        }
        return out;
    }

    /** The total value, or null when no spares were used. */
    static BigDecimal cost(List<SpareUsed> spares) {
        return spares.isEmpty() ? null
                : spares.stream().map(SpareUsed::value).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
