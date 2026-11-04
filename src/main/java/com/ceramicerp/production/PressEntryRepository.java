package com.ceramicerp.production;

import com.ceramicerp.store.DocStatus;
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

public interface PressEntryRepository extends JpaRepository<PressEntry, Long> {

    @EntityGraph(attributePaths = {"shift", "press", "silo"})
    @Query("""
            select e from PressEntry e
            where e.plant.id = :plantId
              and (:status is null or e.status = :status)
              and (:from is null or e.prodDate >= :from)
              and (:to is null or e.prodDate <= :to)
              and (:search is null or lower(e.entryNo) like :search or lower(e.press.code) like :search
                   or lower(e.silo.code) like :search)
            """)
    Page<PressEntry> search(@Param("plantId") Long plantId,
                            @Param("status") DocStatus status,
                            @Param("from") LocalDate from,
                            @Param("to") LocalDate to,
                            @Param("search") String search,
                            Pageable pageable);

    @EntityGraph(attributePaths = {"plant", "shift", "press", "dryer", "silo", "powders", "powders.sprayRun"})
    Optional<PressEntry> findWithDetailsById(Long id);

    /**
     * The posted press entries behind a design lot: the ones made in the same date and shift as one of the lot's
     * posted glaze entries, on the given presses (anyPress = every press of the plant, for a plant with no layout).
     * Green tiles have no design, so the shift is the link: what was pressed in a shift was printed in that shift.
     */
    @EntityGraph(attributePaths = {"shift", "press", "silo"})
    @Query("""
            select e from PressEntry e
            where e.plant.id = :plantId and e.status = com.ceramicerp.store.DocStatus.POSTED
              and (:anyPress = true or e.press.id in :pressIds)
              and exists (select g.id from LineEntry g
                          where g.lot.id = :lotId and g.stage = com.ceramicerp.production.LineStage.GLAZE
                            and g.status = com.ceramicerp.store.DocStatus.POSTED
                            and g.prodDate = e.prodDate and g.shift = e.shift)
            order by e.prodDate, e.id
            """)
    List<PressEntry> findBehindLot(@Param("plantId") Long plantId, @Param("lotId") Long lotId,
                                   @Param("anyPress") boolean anyPress, @Param("pressIds") Collection<Long> pressIds);

    /** Posted entries of a production date, with their press. */
    @EntityGraph(attributePaths = {"press"})
    List<PressEntry> findByPlantIdAndProdDateAndStatus(Long plantId, LocalDate prodDate, DocStatus status);

    /** These entries with their press and shift (the trace from a truck matches them to lots by shift). */
    @EntityGraph(attributePaths = {"press", "shift"})
    List<PressEntry> findByIdIn(Collection<Long> ids);
}
