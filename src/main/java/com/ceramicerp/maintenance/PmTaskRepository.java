package com.ceramicerp.maintenance;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PmTaskRepository extends JpaRepository<PmTask, Long> {

    /** A plant's jobs, soonest due first; every filter is optional. */
    @EntityGraph(attributePaths = {"machine"})
    @Query("""
            select t from PmTask t
            where t.plant.id = :plantId
              and (:active is null or t.active = :active)
              and (:machineId is null or t.machine.id = :machineId)
              and (:dueBy is null or t.nextDue <= :dueBy)
              and (:search is null or lower(t.title) like :search or lower(t.machine.name) like :search
                   or lower(t.machine.code) like :search)
            order by t.nextDue, t.machine.code, t.title
            """)
    List<PmTask> search(@Param("plantId") Long plantId,
                        @Param("active") Boolean active,
                        @Param("machineId") Long machineId,
                        @Param("dueBy") LocalDate dueBy,
                        @Param("search") String search);

    @EntityGraph(attributePaths = {"plant", "machine"})
    Optional<PmTask> findWithDetailsById(Long id);
}
