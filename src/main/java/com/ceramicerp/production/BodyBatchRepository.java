package com.ceramicerp.production;

import com.ceramicerp.production.dto.DayTotal;
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

public interface BodyBatchRepository extends JpaRepository<BodyBatch, Long> {

    @EntityGraph(attributePaths = {"shift", "ballMill", "recipe", "slipTank"})
    @Query("""
            select b from BodyBatch b
            where b.plant.id = :plantId
              and (:status is null or b.status = :status)
              and (:from is null or b.prodDate >= :from)
              and (:to is null or b.prodDate <= :to)
              and (:search is null or lower(b.batchNo) like :search
                   or lower(b.ballMill.code) like :search or lower(b.ballMill.name) like :search)
            """)
    Page<BodyBatch> search(@Param("plantId") Long plantId,
                           @Param("status") BodyBatchStatus status,
                           @Param("from") LocalDate from,
                           @Param("to") LocalDate to,
                           @Param("search") String search,
                           Pageable pageable);

    @EntityGraph(attributePaths = {"plant", "shift", "ballMill", "recipe", "slipTank", "issueDocument",
            "lines", "lines.material", "lines.material.unit", "lines.location"})
    Optional<BodyBatch> findWithDetailsById(Long id);

    /** Batches in one state, oldest first - "what is in the mills now", "what slip is waiting". */
    @EntityGraph(attributePaths = {"shift", "ballMill", "recipe", "slipTank"})
    List<BodyBatch> findByPlantIdAndStatusOrderByMillingStartAsc(Long plantId, BodyBatchStatus status);

    /** Is this mill still holding another charge? */
    Optional<BodyBatch> findFirstByBallMillIdAndStatusAndIdNot(Long ballMillId, BodyBatchStatus status, Long id);

    /** "select new ..." builds a DayTotal object straight from the query - one row with count and sum. */
    @Query("""
            select new com.ceramicerp.production.dto.DayTotal(count(b), sum(b.chargeTon))
            from BodyBatch b
            where b.plant.id = :plantId and b.prodDate = :date and b.status in :statuses
            """)
    DayTotal totalForDay(@Param("plantId") Long plantId, @Param("date") LocalDate date,
                         @Param("statuses") Collection<BodyBatchStatus> statuses);
}
