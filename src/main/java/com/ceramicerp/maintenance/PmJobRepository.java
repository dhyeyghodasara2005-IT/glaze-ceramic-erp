package com.ceramicerp.maintenance;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PmJobRepository extends JpaRepository<PmJob, Long> {

    /** A job's history, newest first. */
    List<PmJob> findByTaskIdOrderByDoneOnDescIdDesc(Long taskId);

    @EntityGraph(attributePaths = {"plant", "task", "task.machine", "issueDocument"})
    Optional<PmJob> findWithDetailsById(Long id);

    /** Jobs done in a plant in a period (for "done on time"). */
    @Query("""
            select j from PmJob j
            where j.plant.id = :plantId and j.status = com.ceramicerp.maintenance.PmJobStatus.DONE
              and j.doneOn between :from and :to
            """)
    List<PmJob> doneBetween(@Param("plantId") Long plantId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
