package com.ceramicerp.store;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface StoreDocumentRepository extends JpaRepository<StoreDocument, Long> {

    /**
     * Document list of one plant and type. "left join" keeps documents that have no supplier / machine
     * (a plain d.supplier.name would silently drop them).
     */
    @EntityGraph(attributePaths = {"supplier", "machine"})
    @Query("""
            select d from StoreDocument d
            left join d.supplier s
            left join d.machine m
            where d.plant.id = :plantId and d.docType = :type
              and (:status is null or d.status = :status)
              and (:from is null or d.docDate >= :from)
              and (:to is null or d.docDate <= :to)
              and (:search is null
                   or lower(d.docNo) like :search or lower(d.supplierInvoiceNo) like :search
                   or lower(s.name) like :search or lower(m.name) like :search
                   or lower(d.issuedTo) like :search or lower(d.vehicleNo) like :search)
            """)
    Page<StoreDocument> search(@Param("plantId") Long plantId,
                               @Param("type") StoreDocType type,
                               @Param("status") DocStatus status,
                               @Param("from") LocalDate from,
                               @Param("to") LocalDate to,
                               @Param("search") String search,
                               Pageable pageable);

    /** One document with everything the screen shows, in one query. */
    @EntityGraph(attributePaths = {"plant", "supplier", "machine", "lines", "lines.material", "lines.material.unit", "lines.location"})
    Optional<StoreDocument> findWithDetailsById(Long id);
}
