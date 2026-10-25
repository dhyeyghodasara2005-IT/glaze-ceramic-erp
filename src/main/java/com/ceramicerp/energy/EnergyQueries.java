package com.ceramicerp.energy;

import com.ceramicerp.master.TileSize;
import com.ceramicerp.production.LineStage;
import com.ceramicerp.store.DocStatus;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;

/** What production made in a period, to divide the energy by. Posted entries only. */
@Repository
@RequiredArgsConstructor
class EnergyQueries {

    private final EntityManager em;

    /** m² packed. */
    BigDecimal sqmPacked(Long plantId, LocalDate from, LocalDate to) {
        return em.createQuery("""
                        select sum(l.boxes * s.sqmPerBox)
                        from PackEntryLine l join l.entry e join e.lot lot join lot.design d join d.tileSize s
                        where e.plant.id = :plantId and e.status = :posted and e.prodDate between :from and :to
                        """, BigDecimal.class)
                .setParameter("plantId", plantId).setParameter("posted", DocStatus.POSTED)
                .setParameter("from", from).setParameter("to", to)
                .getSingleResult();
    }

    /** m² that came out of these kilns (tiles in minus rejects, times the tile's m²). */
    BigDecimal sqmFired(Long plantId, LocalDate from, LocalDate to, Collection<Long> kilnIds) {
        if (kilnIds.isEmpty()) {
            return null;
        }
        BigDecimal sqm = null;
        for (Object[] r : em.createQuery("""
                        select s, sum(e.tilesIn - e.rejects)
                        from LineEntry e join e.lot lot join lot.design d join d.tileSize s
                        where e.plant.id = :plantId and e.status = :posted and e.stage = :kiln
                          and e.prodDate between :from and :to and e.machine.id in :kilns
                        group by s
                        """, Object[].class)
                .setParameter("plantId", plantId).setParameter("posted", DocStatus.POSTED)
                .setParameter("kiln", LineStage.KILN).setParameter("from", from).setParameter("to", to)
                .setParameter("kilns", kilnIds)
                .getResultList()) {
            BigDecimal perTile = ((TileSize) r[0]).sqmPerTile();
            if (perTile != null && r[1] != null) {
                BigDecimal add = perTile.multiply(BigDecimal.valueOf(((Number) r[1]).longValue()));
                sqm = sqm == null ? add : sqm.add(add);
            }
        }
        return sqm;
    }

    /** Tonnes of powder the spray dryers made. */
    BigDecimal powderTon(Long plantId, LocalDate from, LocalDate to) {
        return em.createQuery("""
                        select sum(r.powderTon) from SprayRun r
                        where r.plant.id = :plantId and r.status = :posted and r.prodDate between :from and :to
                        """, BigDecimal.class)
                .setParameter("plantId", plantId).setParameter("posted", DocStatus.POSTED)
                .setParameter("from", from).setParameter("to", to)
                .getSingleResult();
    }
}
