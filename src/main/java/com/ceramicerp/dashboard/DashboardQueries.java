package com.ceramicerp.dashboard;

import com.ceramicerp.master.TileSize;
import com.ceramicerp.production.LineStage;
import com.ceramicerp.sales.SalesOrderStatus;
import com.ceramicerp.store.DocStatus;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The dashboard's totals, read straight from every module's tables. Read only: the dashboard never changes anything.
 * <p>
 * Each query adds up posted documents only (drafts and cancelled ones don't count). They return plain rows
 * (Object[]); DashboardService turns them into the answer. Small result records live here as well.
 */
@Repository
@RequiredArgsConstructor
class DashboardQueries {

    private final EntityManager em;

    // ------------------------------------------------------------------ packing

    record GradeRow(String code, String name, int sortOrder, long boxes, BigDecimal sqm) {
    }

    /** Boxes (and m²) packed per grade in the period. */
    List<GradeRow> packedByGrade(Long plantId, LocalDate from, LocalDate to) {
        return em.createQuery("""
                        select g.code, g.name, g.sortOrder, sum(l.boxes), sum(l.boxes * s.sqmPerBox)
                        from PackEntryLine l join l.grade g join l.entry e join e.lot lot join lot.design d join d.tileSize s
                        where e.plant.id = :plantId and e.status = :posted and e.prodDate between :from and :to
                        group by g.code, g.name, g.sortOrder
                        order by g.sortOrder
                        """, Object[].class)
                .setParameter("plantId", plantId).setParameter("posted", DocStatus.POSTED)
                .setParameter("from", from).setParameter("to", to)
                .getResultList().stream()
                .map(r -> new GradeRow((String) r[0], (String) r[1], (Integer) r[2], toLong(r[3]), (BigDecimal) r[4]))
                .toList();
    }

    record DayRow(LocalDate date, BigDecimal sqm) {
    }

    /** m² packed per production date. */
    List<DayRow> packedByDay(Long plantId, LocalDate from, LocalDate to) {
        return em.createQuery("""
                        select e.prodDate, sum(l.boxes * s.sqmPerBox)
                        from PackEntryLine l join l.entry e join e.lot lot join lot.design d join d.tileSize s
                        where e.plant.id = :plantId and e.status = :posted and e.prodDate between :from and :to
                        group by e.prodDate
                        """, Object[].class)
                .setParameter("plantId", plantId).setParameter("posted", DocStatus.POSTED)
                .setParameter("from", from).setParameter("to", to)
                .getResultList().stream()
                .map(r -> new DayRow((LocalDate) r[0], (BigDecimal) r[1]))
                .toList();
    }

    record PackRow(long sorted, long packed, long broken) {
    }

    PackRow packTotals(Long plantId, LocalDate from, LocalDate to) {
        Object[] r = em.createQuery("""
                        select sum(e.tilesSorted), sum(e.piecesPacked), sum(e.broken)
                        from PackEntry e
                        where e.plant.id = :plantId and e.status = :posted and e.prodDate between :from and :to
                        """, Object[].class)
                .setParameter("plantId", plantId).setParameter("posted", DocStatus.POSTED)
                .setParameter("from", from).setParameter("to", to)
                .getSingleResult();
        return new PackRow(toLong(r[0]), toLong(r[1]), toLong(r[2]));
    }

    // ------------------------------------------------------------------ press and line stages

    record PressRow(long entries, long pressed, long rejects) {
    }

    PressRow pressTotals(Long plantId, LocalDate from, LocalDate to) {
        Object[] r = em.createQuery("""
                        select count(e), sum(e.pressedCount), sum(e.pressRejects + e.dryerRejects)
                        from PressEntry e
                        where e.plant.id = :plantId and e.status = :posted and e.prodDate between :from and :to
                        """, Object[].class)
                .setParameter("plantId", plantId).setParameter("posted", DocStatus.POSTED)
                .setParameter("from", from).setParameter("to", to)
                .getSingleResult();
        return new PressRow(toLong(r[0]), toLong(r[1]), toLong(r[2]));
    }

    record StageRow(LineStage stage, long entries, long tilesIn, long rejects, BigDecimal gasScm, BigDecimal powerKwh) {
    }

    List<StageRow> stageTotals(Long plantId, LocalDate from, LocalDate to) {
        return em.createQuery("""
                        select e.stage, count(e), sum(e.tilesIn), sum(e.rejects), sum(e.gasScm), sum(e.powerKwh)
                        from LineEntry e
                        where e.plant.id = :plantId and e.status = :posted and e.prodDate between :from and :to
                        group by e.stage
                        """, Object[].class)
                .setParameter("plantId", plantId).setParameter("posted", DocStatus.POSTED)
                .setParameter("from", from).setParameter("to", to)
                .getResultList().stream()
                .map(r -> new StageRow((LineStage) r[0], toLong(r[1]), toLong(r[2]), toLong(r[3]), (BigDecimal) r[4],
                        (BigDecimal) r[5]))
                .toList();
    }

    record FiredRow(TileSize size, long tiles) {
    }

    /** Tiles out of the kiln per tile size (m² needs the size). */
    List<FiredRow> firedBySize(Long plantId, LocalDate from, LocalDate to) {
        return em.createQuery("""
                        select s, sum(e.tilesIn - e.rejects)
                        from LineEntry e join e.lot lot join lot.design d join d.tileSize s
                        where e.plant.id = :plantId and e.status = :posted and e.stage = :kiln
                          and e.prodDate between :from and :to
                        group by s
                        """, Object[].class)
                .setParameter("plantId", plantId).setParameter("posted", DocStatus.POSTED)
                .setParameter("kiln", LineStage.KILN).setParameter("from", from).setParameter("to", to)
                .getResultList().stream()
                .map(r -> new FiredRow((TileSize) r[0], toLong(r[1])))
                .toList();
    }

    // ------------------------------------------------------------------ sales

    record DispatchRow(LocalDate date, long orders, long boxes, BigDecimal sqm) {
    }

    /** Boxes loaded per dispatch date (orders still dispatched now; undone dispatches don't count). */
    List<DispatchRow> dispatchedByDay(Long plantId, LocalDate from, LocalDate to) {
        return em.createQuery("""
                        select o.dispatchDate, count(distinct o), sum(p.loadedBoxes), sum(p.loadedBoxes * s.sqmPerBox)
                        from SalesOrderPick p join p.order o join p.stock st join st.design d join d.tileSize s
                        where o.plant.id = :plantId and o.status = :dispatched and o.dispatchDate between :from and :to
                          and p.loadedBoxes is not null
                        group by o.dispatchDate
                        """, Object[].class)
                .setParameter("plantId", plantId).setParameter("dispatched", SalesOrderStatus.DISPATCHED)
                .setParameter("from", from).setParameter("to", to)
                .getResultList().stream()
                .map(r -> new DispatchRow((LocalDate) r[0], toLong(r[1]), toLong(r[2]), (BigDecimal) r[3]))
                .toList();
    }

    record WaitingLine(Long lineId, Long orderId, String designName, String gradeName, int boxes) {
    }

    /** Every line of the confirmed orders (waiting for or being loaded). */
    List<WaitingLine> waitingLines(Long plantId) {
        return em.createQuery("""
                        select l.id, o.id, d.name, g.name, l.boxes
                        from SalesOrderLine l join l.order o join l.design d join l.grade g
                        where o.plant.id = :plantId and o.status = :confirmed
                        """, Object[].class)
                .setParameter("plantId", plantId).setParameter("confirmed", SalesOrderStatus.CONFIRMED)
                .getResultList().stream()
                .map(r -> new WaitingLine((Long) r[0], (Long) r[1], (String) r[2], (String) r[3], (Integer) r[4]))
                .toList();
    }

    record LineKept(Long lineId, long boxes) {
    }

    /** Boxes each confirmed order line keeps (loaded once ticked, else planned). */
    List<LineKept> keptByLine(Long plantId) {
        return em.createQuery("""
                        select p.line.id, sum(coalesce(p.loadedBoxes, p.plannedBoxes))
                        from SalesOrderPick p
                        where p.order.plant.id = :plantId and p.order.status = :confirmed
                        group by p.line.id
                        """, Object[].class)
                .setParameter("plantId", plantId).setParameter("confirmed", SalesOrderStatus.CONFIRMED)
                .getResultList().stream()
                .map(r -> new LineKept((Long) r[0], toLong(r[1])))
                .toList();
    }

    record StockKept(Long stockId, int boxesThere, long kept) {
    }

    /** Per stock row: the boxes there and what the confirmed orders keep from it. */
    List<StockKept> keptByStock(Long plantId) {
        return em.createQuery("""
                        select st.id, st.boxes, sum(coalesce(p.loadedBoxes, p.plannedBoxes))
                        from SalesOrderPick p join p.stock st
                        where p.order.plant.id = :plantId and p.order.status = :confirmed
                        group by st.id, st.boxes
                        """, Object[].class)
                .setParameter("plantId", plantId).setParameter("confirmed", SalesOrderStatus.CONFIRMED)
                .getResultList().stream()
                .map(r -> new StockKept((Long) r[0], (Integer) r[1], toLong(r[2])))
                .toList();
    }

    // ------------------------------------------------------------------ warehouse and store

    record PillarRow(long pillars, Long capacity) {
    }

    /** Open pillars of the plant's open godowns, and their capacity in pallets (where it is set). */
    PillarRow pillars(Long plantId) {
        Object[] r = em.createQuery("""
                        select count(p), sum(p.capacityPallets)
                        from FgPillar p
                        where p.godown.plant.id = :plantId and p.active = true and p.godown.active = true
                        """, Object[].class)
                .setParameter("plantId", plantId)
                .getSingleResult();
        return new PillarRow(toLong(r[0]), r[1] == null ? null : toLong(r[1]));
    }

    BigDecimal storeValue(Long plantId) {
        BigDecimal value = em.createQuery("select sum(b.value) from StockBalance b where b.plant.id = :plantId",
                        BigDecimal.class)
                .setParameter("plantId", plantId)
                .getSingleResult();
        return value == null ? BigDecimal.ZERO : value;
    }

    /** Names of active materials whose stock in this plant is at or below their reorder level. */
    List<String> lowMaterials(Long plantId) {
        return em.createQuery("""
                        select m.name
                        from Material m
                        where m.active = true and m.reorderLevel is not null
                          and coalesce((select sum(b.quantity) from StockBalance b
                                        where b.material = m and b.plant.id = :plantId), 0) <= m.reorderLevel
                        order by m.name
                        """, String.class)
                .setParameter("plantId", plantId)
                .getResultList();
    }

    /** sum() of whole numbers comes back as a Long, or null when there are no rows. */
    private static long toLong(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }
}
