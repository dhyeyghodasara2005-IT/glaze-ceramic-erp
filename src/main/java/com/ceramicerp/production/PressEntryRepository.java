package com.ceramicerp.production;

import com.ceramicerp.production.dto.LotTotals;
import com.ceramicerp.store.DocStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PressEntryRepository extends JpaRepository<PressEntry, Long> {

    @EntityGraph(attributePaths = {"lot", "lot.design", "shift", "press", "silo"})
    @Query("""
            select e from PressEntry e
            where e.plant.id = :plantId
              and (:status is null or e.status = :status)
              and (:from is null or e.prodDate >= :from)
              and (:to is null or e.prodDate <= :to)
              and (:search is null or lower(e.entryNo) like :search or lower(e.lot.lotNo) like :search
                   or lower(e.lot.design.name) like :search)
            """)
    Page<PressEntry> search(@Param("plantId") Long plantId,
                            @Param("status") DocStatus status,
                            @Param("from") LocalDate from,
                            @Param("to") LocalDate to,
                            @Param("search") String search,
                            Pageable pageable);

    @EntityGraph(attributePaths = {"plant", "lot", "lot.design", "lot.design.tileSize", "shift", "press", "dryer", "silo",
            "powders", "powders.sprayRun"})
    Optional<PressEntry> findWithDetailsById(Long id);

    /** Every entry of one lot, oldest first (drafts and cancelled ones too - the lot page shows them all). */
    @EntityGraph(attributePaths = {"lot", "lot.design", "shift", "press", "silo"})
    List<PressEntry> findByLotIdOrderByProdDateAscIdAsc(Long lotId);

    boolean existsByLotIdAndStatus(Long lotId, DocStatus status);

    /**
     * Totals of POSTED entries for many lots in ONE query: "group by" gives one row per lot.
     * (Asking lot by lot would be one query per row of the list - the N+1 problem again.)
     */
    @Query("""
            select new com.ceramicerp.production.dto.LotTotals(e.lot.id, count(e), sum(e.pressedCount),
                   sum(e.pressRejects), sum(e.dryerRejects), sum(e.powderTon), sum(e.untracedTon))
            from PressEntry e
            where e.lot.id in :lotIds and e.status = com.ceramicerp.store.DocStatus.POSTED
            group by e.lot.id
            """)
    List<LotTotals> totalsForLots(@Param("lotIds") Collection<Long> lotIds);

    /** Posted entries of a production date, with the size (for square metres). */
    @EntityGraph(attributePaths = {"lot", "lot.design", "lot.design.tileSize"})
    List<PressEntry> findByPlantIdAndProdDateAndStatus(Long plantId, LocalDate prodDate, DocStatus status);
}
