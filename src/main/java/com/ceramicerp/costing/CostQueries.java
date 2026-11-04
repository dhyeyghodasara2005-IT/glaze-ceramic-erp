package com.ceramicerp.costing;

import com.ceramicerp.master.TileSize;
import com.ceramicerp.production.LineStage;
import com.ceramicerp.store.DocStatus;
import com.ceramicerp.store.StoreDocType;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The totals a month's cost is made from, read from the store and packing. Posted documents only. */
@Repository
@RequiredArgsConstructor
class CostQueries {

    private final EntityManager em;

    record Issued(String groupCode, String groupName, BigDecimal value) {
    }

    /** What left the store in the period, at average cost, per material group. A cancelled issue doesn't count. */
    List<Issued> issuedByGroup(Long plantId, LocalDate from, LocalDate to) {
        return em.createQuery("""
                        select g.code, g.name, sum(l.value)
                        from StoreDocumentLine l join l.document d join l.material m join m.group g
                        where d.plant.id = :plantId and d.docType = :issue and d.status = :posted
                          and d.docDate between :from and :to
                        group by g.code, g.name
                        """, Object[].class)
                .setParameter("plantId", plantId).setParameter("issue", StoreDocType.ISSUE)
                .setParameter("posted", DocStatus.POSTED).setParameter("from", from).setParameter("to", to)
                .getResultList().stream()
                .map(r -> new Issued((String) r[0], (String) r[1], (BigDecimal) r[2]))
                .toList();
    }

    record PackedDesign(Long designId, String code, String name, long boxes, BigDecimal sqm) {
    }

    /** Boxes and m² packed per design. */
    List<PackedDesign> packedByDesign(Long plantId, LocalDate from, LocalDate to) {
        return em.createQuery("""
                        select d.id, d.code, d.name, sum(l.boxes), sum(l.boxes * s.sqmPerBox)
                        from PackEntryLine l join l.entry e join e.lot lot join lot.design d join d.tileSize s
                        where e.plant.id = :plantId and e.status = :posted and e.prodDate between :from and :to
                        group by d.id, d.code, d.name
                        """, Object[].class)
                .setParameter("plantId", plantId).setParameter("posted", DocStatus.POSTED)
                .setParameter("from", from).setParameter("to", to)
                .getResultList().stream()
                .map(r -> new PackedDesign((Long) r[0], (String) r[1], (String) r[2], ((Number) r[3]).longValue(), (BigDecimal) r[4]))
                .toList();
    }

    record GlazedDesign(Long designId, String code, String name, BigDecimal sqm) {
    }

    /**
     * Square metres glazed per design: tiles in at the glaze line x the tile's m². Body cost is shared by this, not
     * by the powder pressed: the press makes green tiles with no design, so its rejects can't be blamed on one.
     */
    List<GlazedDesign> glazedByDesign(Long plantId, LocalDate from, LocalDate to) {
        Map<Long, GlazedDesign> out = new LinkedHashMap<>();
        for (Object[] r : em.createQuery("""
                        select d.id, d.code, d.name, s, sum(e.tilesIn)
                        from LineEntry e join e.lot lot join lot.design d join d.tileSize s
                        where e.plant.id = :plantId and e.status = :posted and e.stage = :glaze
                          and e.prodDate between :from and :to
                        group by d.id, d.code, d.name, s
                        """, Object[].class)
                .setParameter("plantId", plantId).setParameter("posted", DocStatus.POSTED)
                .setParameter("glaze", LineStage.GLAZE).setParameter("from", from).setParameter("to", to)
                .getResultList()) {
            BigDecimal perTile = ((TileSize) r[3]).sqmPerTile();
            if (perTile == null || r[4] == null) {
                continue;
            }
            BigDecimal sqm = perTile.multiply(BigDecimal.valueOf(((Number) r[4]).longValue()));
            out.merge((Long) r[0], new GlazedDesign((Long) r[0], (String) r[1], (String) r[2], sqm),
                    (a, b) -> new GlazedDesign(a.designId(), a.code(), a.name(), a.sqm().add(b.sqm())));
        }
        return List.copyOf(out.values());
    }

    record GlazeEntry(Long designId, String code, String name, int tilesIn, BigDecimal engobeGpt, BigDecimal glazeGpt,
                      BigDecimal inkLitres) {
    }

    /** Each posted glaze-line entry: what it put on the tiles of which design. */
    List<GlazeEntry> glazeEntries(Long plantId, LocalDate from, LocalDate to) {
        return em.createQuery("""
                        select d.id, d.code, d.name, e.tilesIn, e.engobeGpt, e.glazeGpt, e.inkLitres
                        from LineEntry e join e.lot lot join lot.design d
                        where e.plant.id = :plantId and e.status = :posted and e.stage = :glaze
                          and e.prodDate between :from and :to
                        """, Object[].class)
                .setParameter("plantId", plantId).setParameter("posted", DocStatus.POSTED)
                .setParameter("glaze", LineStage.GLAZE).setParameter("from", from).setParameter("to", to)
                .getResultList().stream()
                .map(r -> new GlazeEntry((Long) r[0], (String) r[1], (String) r[2], (Integer) r[3], (BigDecimal) r[4],
                        (BigDecimal) r[5], (BigDecimal) r[6]))
                .toList();
    }

    record Packed(long boxes, BigDecimal sqm) {
    }

    /** Boxes and m² packed in the period. */
    Packed packed(Long plantId, LocalDate from, LocalDate to) {
        Object[] r = em.createQuery("""
                        select sum(l.boxes), sum(l.boxes * s.sqmPerBox)
                        from PackEntryLine l join l.entry e join e.lot lot join lot.design d join d.tileSize s
                        where e.plant.id = :plantId and e.status = :posted and e.prodDate between :from and :to
                        """, Object[].class)
                .setParameter("plantId", plantId).setParameter("posted", DocStatus.POSTED)
                .setParameter("from", from).setParameter("to", to)
                .getSingleResult();
        return new Packed(r[0] == null ? 0 : ((Number) r[0]).longValue(), r[1] == null ? BigDecimal.ZERO : (BigDecimal) r[1]);
    }
}
