package com.ceramicerp.fg;

import com.ceramicerp.store.DocStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface FgDocumentRepository extends JpaRepository<FgDocument, Long> {

    @Query("""
            select d from FgDocument d
            where d.plant.id = :plantId and d.docType = :type
              and (:status is null or d.status = :status)
              and (:from is null or d.docDate >= :from)
              and (:to is null or d.docDate <= :to)
              and (:search is null or lower(d.docNo) like :search or lower(d.remarks) like :search)
            """)
    Page<FgDocument> search(@Param("plantId") Long plantId,
                            @Param("type") FgDocType type,
                            @Param("status") DocStatus status,
                            @Param("from") LocalDate from,
                            @Param("to") LocalDate to,
                            @Param("search") String search,
                            Pageable pageable);

    @EntityGraph(attributePaths = {"plant"})
    Optional<FgDocument> findWithPlantById(Long id);
}
