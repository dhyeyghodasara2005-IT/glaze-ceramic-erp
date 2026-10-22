package com.ceramicerp.production;

import com.ceramicerp.production.dto.DayTotal;
import com.ceramicerp.store.DocStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SprayRunRepository extends JpaRepository<SprayRun, Long> {

    @EntityGraph(attributePaths = {"shift", "sprayDryer", "silo"})
    @Query("""
            select r from SprayRun r
            where r.plant.id = :plantId
              and (:status is null or r.status = :status)
              and (:from is null or r.prodDate >= :from)
              and (:to is null or r.prodDate <= :to)
              and (:search is null or lower(r.runNo) like :search
                   or lower(r.silo.code) like :search or lower(r.silo.name) like :search)
            """)
    Page<SprayRun> search(@Param("plantId") Long plantId,
                          @Param("status") DocStatus status,
                          @Param("from") LocalDate from,
                          @Param("to") LocalDate to,
                          @Param("search") String search,
                          Pageable pageable);

    @EntityGraph(attributePaths = {"plant", "shift", "sprayDryer", "silo", "batches", "batches.bodyBatch",
            "batches.bodyBatch.ballMill", "batches.bodyBatch.slipTank"})
    Optional<SprayRun> findWithDetailsById(Long id);

    @Query("""
            select new com.ceramicerp.production.dto.DayTotal(count(r), sum(r.powderTon))
            from SprayRun r
            where r.plant.id = :plantId and r.prodDate = :date and r.status = :status
            """)
    DayTotal totalForDay(@Param("plantId") Long plantId, @Param("date") LocalDate date,
                         @Param("status") DocStatus status);

    /**
     * The powder in a silo, oldest first (FIFO: first in, first out - powder ages in the silo, so the
     * press takes the oldest). Locked (SELECT ... FOR UPDATE) because posting changes powderLeft.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from SprayRun r
            where r.silo.id = :siloId and r.status = com.ceramicerp.store.DocStatus.POSTED and r.powderLeft > 0
            order by r.runStart asc, r.id asc
            """)
    List<SprayRun> findForDraw(@Param("siloId") Long siloId);

    /** Everything still in the silos of a plant, oldest first. */
    @EntityGraph(attributePaths = {"silo"})
    List<SprayRun> findByPlantIdAndStatusAndPowderLeftGreaterThanOrderByRunStartAsc(Long plantId, DocStatus status,
                                                                                    BigDecimal minimum);

    /** Body batches of these runs, for the lot trace. */
    @Query("""
            select rb from SprayRunBatch rb join fetch rb.bodyBatch
            where rb.run.id in :runIds
            order by rb.bodyBatch.id
            """)
    List<SprayRunBatch> findBatchesOfRuns(@Param("runIds") Collection<Long> runIds);
}
