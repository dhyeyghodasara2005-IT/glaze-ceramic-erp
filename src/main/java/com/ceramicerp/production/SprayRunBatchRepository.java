package com.ceramicerp.production;

import com.ceramicerp.store.DocStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SprayRunBatchRepository extends JpaRepository<SprayRunBatch, Long> {

    /** Runs (in one status) that used a body batch - the trace shown on the batch screen. */
    @Query("""
            select rb from SprayRunBatch rb join fetch rb.run r
            where rb.bodyBatch.id = :batchId and r.status = :status
            order by r.id
            """)
    List<SprayRunBatch> findForBatch(@Param("batchId") Long batchId, @Param("status") DocStatus status);
}
