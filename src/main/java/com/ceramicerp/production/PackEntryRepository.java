package com.ceramicerp.production;

import com.ceramicerp.production.dto.GradeCount;
import com.ceramicerp.production.dto.LotCount;
import com.ceramicerp.production.dto.PackTotals;
import com.ceramicerp.production.dto.PackedBatch;
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

public interface PackEntryRepository extends JpaRepository<PackEntry, Long> {

    @EntityGraph(attributePaths = {"lot", "lot.design", "lot.design.tileSize", "shift", "machine"})
    @Query("""
            select e from PackEntry e
            where e.plant.id = :plantId
              and (:status is null or e.status = :status)
              and (:from is null or e.prodDate >= :from)
              and (:to is null or e.prodDate <= :to)
              and (:search is null or lower(e.entryNo) like :search or lower(e.lot.lotNo) like :search
                   or lower(e.lot.design.name) like :search)
            """)
    Page<PackEntry> search(@Param("plantId") Long plantId,
                           @Param("status") DocStatus status,
                           @Param("from") LocalDate from,
                           @Param("to") LocalDate to,
                           @Param("search") String search,
                           Pageable pageable);

    @EntityGraph(attributePaths = {"plant", "lot", "lot.design", "lot.design.tileSize", "lot.press", "shift", "machine"})
    Optional<PackEntry> findWithDetailsById(Long id);

    /** Posted totals per lot, for many lots in one query. */
    @Query("""
            select new com.ceramicerp.production.dto.PackTotals(e.lot.id, count(e), sum(e.tilesSorted), sum(e.broken),
                   sum(e.piecesPacked), sum(e.boxes))
            from PackEntry e
            where e.lot.id in :lotIds and e.status = com.ceramicerp.store.DocStatus.POSTED
            group by e.lot.id
            """)
    List<PackTotals> totalsForLots(@Param("lotIds") Collection<Long> lotIds);

    /** A lot's posted boxes per grade + shade + caliber, best grade first. */
    @Query("""
            select new com.ceramicerp.production.dto.PackedBatch(g.id, g.code, g.name, g.sortOrder, l.shade, l.caliber,
                   sum(l.boxes), sum(l.pieces))
            from PackEntryLine l join l.grade g
            where l.entry.lot.id = :lotId and l.entry.status = com.ceramicerp.store.DocStatus.POSTED
            group by g.id, g.code, g.name, g.sortOrder, l.shade, l.caliber
            order by g.sortOrder, l.shade, l.caliber
            """)
    List<PackedBatch> packedBatches(@Param("lotId") Long lotId);

    /** Posted pieces of one grade per lot (how much of each lot went into the top grade). */
    @Query("""
            select new com.ceramicerp.production.dto.LotCount(l.entry.lot.id, sum(l.pieces))
            from PackEntryLine l
            where l.entry.lot.id in :lotIds and l.grade.id = :gradeId
              and l.entry.status = com.ceramicerp.store.DocStatus.POSTED
            group by l.entry.lot.id
            """)
    List<LotCount> piecesOfGrade(@Param("lotIds") Collection<Long> lotIds, @Param("gradeId") Long gradeId);

    /** Boxes per grade packed in a plant on a production date. */
    @Query("""
            select new com.ceramicerp.production.dto.GradeCount(g.id, g.code, g.name, g.sortOrder, sum(l.boxes), sum(l.pieces))
            from PackEntryLine l join l.grade g
            where l.entry.plant.id = :plantId and l.entry.prodDate = :prodDate
              and l.entry.status = com.ceramicerp.store.DocStatus.POSTED
            group by g.id, g.code, g.name, g.sortOrder
            order by g.sortOrder
            """)
    List<GradeCount> gradesOfDay(@Param("plantId") Long plantId, @Param("prodDate") LocalDate prodDate);

    /** Shades a lot has used (drafts too: the next shift often continues a shade). */
    @Query("""
            select distinct l.shade from PackEntryLine l
            where l.entry.lot.id = :lotId and l.entry.status <> com.ceramicerp.store.DocStatus.CANCELLED
            order by l.shade
            """)
    List<String> shadesOfLot(@Param("lotId") Long lotId);

    @Query("""
            select distinct l.caliber from PackEntryLine l
            where l.entry.lot.id = :lotId and l.entry.status <> com.ceramicerp.store.DocStatus.CANCELLED
            order by l.caliber
            """)
    List<String> calibersOfLot(@Param("lotId") Long lotId);

    @EntityGraph(attributePaths = {"lot", "lot.design", "lot.design.tileSize", "shift", "machine"})
    List<PackEntry> findByLotIdOrderByProdDateAscIdAsc(Long lotId);

    @EntityGraph(attributePaths = {"lot", "lot.design", "lot.design.tileSize"})
    List<PackEntry> findByPlantIdAndProdDateAndStatus(Long plantId, LocalDate prodDate, DocStatus status);
}
