package com.ceramicerp.production;

import com.ceramicerp.production.dto.DayTotal;
import com.ceramicerp.store.DocStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
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
}
