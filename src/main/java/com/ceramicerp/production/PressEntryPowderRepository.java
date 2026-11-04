package com.ceramicerp.production;

import com.ceramicerp.store.DocStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PressEntryPowderRepository extends JpaRepository<PressEntryPowder, Long> {

    /** Powder links of these press entries, with each spray run and its silo - the start of a lot's trace. */
    @Query("""
            select p from PressEntryPowder p
            join fetch p.sprayRun r join fetch r.silo
            where p.entry.id in :entryIds
            order by r.runStart, r.id
            """)
    List<PressEntryPowder> findForEntries(@Param("entryIds") Collection<Long> entryIds);

    /** The posted press entries that drew powder made from these mill charges (the trace from a truck to tiles). */
    @Query("""
            select distinct p.entry.id from PressEntryPowder p, SprayRunBatch rb
            where p.sprayRun = rb.run and rb.bodyBatch.id in :batchIds
              and p.entry.status = :status and rb.run.status = :status
            """)
    List<Long> findEntriesFromBatches(@Param("batchIds") Collection<Long> batchIds, @Param("status") DocStatus status);

    /** Has any entry in this status used powder from this run? (stops cancelling a run that was pressed) */
    @Query("""
            select p from PressEntryPowder p join fetch p.entry e
            where p.sprayRun.id = :runId and e.status = :status
            order by e.id
            """)
    List<PressEntryPowder> findForRun(@Param("runId") Long runId, @Param("status") DocStatus status);
}
