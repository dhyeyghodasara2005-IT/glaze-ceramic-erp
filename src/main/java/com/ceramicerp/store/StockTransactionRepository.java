package com.ceramicerp.store;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface StockTransactionRepository extends JpaRepository<StockTransaction, Long> {

    /** The ledger of one material in a plant (optionally one godown, optionally a date range), newest first. */
    @EntityGraph(attributePaths = {"location"})
    @Query("""
            select t from StockTransaction t
            where t.plant.id = :plantId and t.material.id = :materialId
              and (:locationId is null or t.location.id = :locationId)
              and (:from is null or t.txnDate >= :from)
              and (:to is null or t.txnDate <= :to)
            """)
    Page<StockTransaction> ledger(@Param("plantId") Long plantId,
                                  @Param("materialId") Long materialId,
                                  @Param("locationId") Long locationId,
                                  @Param("from") LocalDate from,
                                  @Param("to") LocalDate to,
                                  Pageable pageable);

    /** Movements written by one document (used to reverse them exactly on cancel). */
    @EntityGraph(attributePaths = {"location", "material", "material.unit"})
    List<StockTransaction> findByDocumentIdAndReversalFalseOrderByIdAsc(Long documentId);
}
