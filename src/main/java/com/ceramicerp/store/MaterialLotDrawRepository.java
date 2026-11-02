package com.ceramicerp.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MaterialLotDrawRepository extends JpaRepository<MaterialLotDraw, Long> {

    /** What a movement took: put back when its document is cancelled. */
    @Query("select d from MaterialLotDraw d join fetch d.lot where d.transaction.id = :transactionId and d.reversed = false")
    List<MaterialLotDraw> findActiveForTransaction(@Param("transactionId") Long transactionId);

    /** The first document that used a lot, to name it when the lot's GRN can't be cancelled. */
    Optional<MaterialLotDraw> findFirstByLotIdAndReversedFalseOrderByIdAsc(Long lotId);

    /** Everything that used a lot, newest first. */
    @Query("""
            select d from MaterialLotDraw d
            join fetch d.transaction t join fetch t.document doc left join fetch doc.machine
            where d.lot.id = :lotId and d.reversed = false
            order by d.id desc
            """)
    List<MaterialLotDraw> findForLot(@Param("lotId") Long lotId);

    /** The lots these documents took from: the start of the trace from a mill charge back to the trucks. */
    @Query("""
            select d from MaterialLotDraw d
            join fetch d.transaction t
            join fetch d.lot l join fetch l.material m join fetch m.unit join fetch l.location
            left join fetch l.supplier left join fetch l.document left join fetch l.line
            where t.document.id in :documentIds and t.reversal = false and d.reversed = false
            order by l.receivedOn, l.id, d.id
            """)
    List<MaterialLotDraw> findForDocuments(@Param("documentIds") Collection<Long> documentIds);
}
