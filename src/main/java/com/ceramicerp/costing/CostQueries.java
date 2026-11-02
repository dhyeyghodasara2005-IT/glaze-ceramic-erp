package com.ceramicerp.costing;

import com.ceramicerp.store.DocStatus;
import com.ceramicerp.store.StoreDocType;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
