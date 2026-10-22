package com.ceramicerp.production;

import com.ceramicerp.store.DocStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PressEntryPowderRepository extends JpaRepository<PressEntryPowder, Long> {

    /** Powder links of a lot's posted entries, with each spray run and its silo - the start of the trace. */
    @Query("""
            select p from PressEntryPowder p
            join fetch p.sprayRun r join fetch r.silo
            where p.entry.lot.id = :lotId and p.entry.status = :status
            order by r.runStart, r.id
            """)
    List<PressEntryPowder> findForLot(@Param("lotId") Long lotId, @Param("status") DocStatus status);

    /** Has any entry in this status used powder from this run? (stops cancelling a run that was pressed) */
    @Query("""
            select p from PressEntryPowder p join fetch p.entry e
            where p.sprayRun.id = :runId and e.status = :status
            order by e.id
            """)
    List<PressEntryPowder> findForRun(@Param("runId") Long runId, @Param("status") DocStatus status);
}
