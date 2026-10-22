package com.ceramicerp.production;

import com.ceramicerp.production.dto.StageTotals;
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

public interface LineEntryRepository extends JpaRepository<LineEntry, Long> {

    @EntityGraph(attributePaths = {"lot", "lot.design", "shift", "machine"})
    @Query("""
            select e from LineEntry e
            where e.plant.id = :plantId and e.stage = :stage
              and (:status is null or e.status = :status)
              and (:from is null or e.prodDate >= :from)
              and (:to is null or e.prodDate <= :to)
              and (:search is null or lower(e.entryNo) like :search or lower(e.lot.lotNo) like :search
                   or lower(e.lot.design.name) like :search)
            """)
    Page<LineEntry> search(@Param("plantId") Long plantId,
                           @Param("stage") LineStage stage,
                           @Param("status") DocStatus status,
                           @Param("from") LocalDate from,
                           @Param("to") LocalDate to,
                           @Param("search") String search,
                           Pageable pageable);

    @EntityGraph(attributePaths = {"plant", "lot", "lot.design", "lot.design.tileSize", "lot.press", "shift", "machine", "printer"})
    Optional<LineEntry> findWithDetailsById(Long id);

    /**
     * Posted tiles in / rejected per lot AND stage, for many lots in one query.
     * "group by" two columns gives one row per (lot, stage) pair.
     */
    @Query("""
            select new com.ceramicerp.production.dto.StageTotals(e.lot.id, e.stage, count(e), sum(e.tilesIn),
                   sum(e.rejects), sum(e.gasScm))
            from LineEntry e
            where e.lot.id in :lotIds and e.status = com.ceramicerp.store.DocStatus.POSTED
            group by e.lot.id, e.stage
            """)
    List<StageTotals> totalsForLots(@Param("lotIds") Collection<Long> lotIds);

    /** Every entry of one lot and stage, oldest first (the lot page lists them). */
    @EntityGraph(attributePaths = {"lot", "lot.design", "shift", "machine"})
    List<LineEntry> findByLotIdAndStageOrderByProdDateAscIdAsc(Long lotId, LineStage stage);

    /** Posted entries of a production date, with the size (for square metres). */
    @EntityGraph(attributePaths = {"lot", "lot.design", "lot.design.tileSize"})
    List<LineEntry> findByPlantIdAndProdDateAndStatus(Long plantId, LocalDate prodDate, DocStatus status);
}
