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

public interface LotCheckRepository extends JpaRepository<LotCheck, Long> {

    @EntityGraph(attributePaths = {"plant", "lot", "lot.design", "lot.press", "shift"})
    @Query("""
            select c from LotCheck c
            where c.plant.id = :plantId
              and (:status is null or c.status = :status)
              and (:from is null or c.checkDate >= :from)
              and (:to is null or c.checkDate <= :to)
              and (:search is null or lower(c.checkNo) like :search or lower(c.lot.lotNo) like :search
                   or lower(c.lot.design.name) like :search)
            """)
    Page<LotCheck> search(@Param("plantId") Long plantId,
                          @Param("status") DocStatus status,
                          @Param("from") LocalDate from,
                          @Param("to") LocalDate to,
                          @Param("search") String search,
                          Pageable pageable);

    @EntityGraph(attributePaths = {"plant", "lot", "lot.design", "lot.press", "shift"})
    Optional<LotCheck> findWithDetailsById(Long id);

    /** Every check of a lot, oldest first (the lot page lists them). */
    @EntityGraph(attributePaths = {"plant", "lot", "lot.design", "lot.press", "shift"})
    List<LotCheck> findByLotIdOrderByCheckDateAscIdAsc(Long lotId);

    /** Posted checks of many lots, newest first: the first one per lot is its latest. */
    @Query("""
            select c from LotCheck c
            where c.lot.id in :lotIds and c.status = com.ceramicerp.store.DocStatus.POSTED
            order by c.checkDate desc, c.id desc
            """)
    List<LotCheck> findPostedForLots(@Param("lotIds") Collection<Long> lotIds);

    long countByPlantIdAndCheckDateAndStatus(Long plantId, LocalDate checkDate, DocStatus status);
}
