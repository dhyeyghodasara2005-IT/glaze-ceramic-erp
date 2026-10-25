package com.ceramicerp.maintenance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BreakdownRepository extends JpaRepository<Breakdown, Long> {

    @EntityGraph(attributePaths = {"machine"})
    @Query("""
            select b from Breakdown b
            where b.plant.id = :plantId
              and (:status is null or b.status = :status)
              and (:machineId is null or b.machine.id = :machineId)
              and (:from is null or b.stoppedAt >= :from)
              and (:to is null or b.stoppedAt < :to)
              and (:search is null or lower(b.breakdownNo) like :search or lower(b.symptom) like :search
                   or lower(b.machine.name) like :search or lower(b.machine.code) like :search
                   or lower(b.failedPart) like :search)
            """)
    Page<Breakdown> search(@Param("plantId") Long plantId,
                           @Param("status") BreakdownStatus status,
                           @Param("machineId") Long machineId,
                           @Param("from") LocalDateTime from,
                           @Param("to") LocalDateTime to,
                           @Param("search") String search,
                           Pageable pageable);

    @EntityGraph(attributePaths = {"plant", "machine", "issueDocument"})
    Optional<Breakdown> findWithDetailsById(Long id);

    /** The machine's breakdown that is still open, if any (there is at most one). */
    Optional<Breakdown> findByMachineIdAndStatus(Long machineId, BreakdownStatus status);

    /** Machines down right now, the longest first. */
    @EntityGraph(attributePaths = {"machine"})
    List<Breakdown> findByPlantIdAndStatusOrderByStoppedAtAsc(Long plantId, BreakdownStatus status);

    /**
     * Breakdowns that overlap a period: stopped before it ends, and not restarted before it starts (open ones
     * count up to now). Cancelled reports are left out.
     */
    @EntityGraph(attributePaths = {"machine"})
    @Query("""
            select b from Breakdown b
            where b.plant.id = :plantId and b.status <> com.ceramicerp.maintenance.BreakdownStatus.CANCELLED
              and b.stoppedAt < :to and (b.restartedAt is null or b.restartedAt > :from)
            """)
    List<Breakdown> overlapping(@Param("plantId") Long plantId,
                                @Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to);
}
