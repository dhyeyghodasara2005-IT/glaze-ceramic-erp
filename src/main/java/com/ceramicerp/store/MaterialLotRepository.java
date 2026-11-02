package com.ceramicerp.store;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MaterialLotRepository extends JpaRepository<MaterialLot, Long> {

    /** What is left of a material in a godown, oldest first: the order it gets used in. */
    @Query("""
            select l from MaterialLot l left join fetch l.line
            where l.location.id = :locationId and l.material.id = :materialId and l.quantityLeft > 0
            order by l.receivedOn, l.id
            """)
    List<MaterialLot> findOpen(@Param("locationId") Long locationId, @Param("materialId") Long materialId);

    Optional<MaterialLot> findByTransactionId(Long transactionId);

    /** @param open true = some left, false = used up, null = every lot, also cancelled ones */
    @Query(value = """
            select l from MaterialLot l
            join fetch l.material m join fetch m.unit join fetch l.location
            left join fetch l.supplier s left join fetch l.document d left join fetch l.line
            where l.plant.id = :plantId
              and (:materialId is null or m.id = :materialId)
              and (:locationId is null or l.location.id = :locationId)
              and (:open is null or (:open = true and l.quantityLeft > 0)
                   or (:open = false and l.quantityLeft = 0 and l.cancelled = false))
              and (:pattern is null or lower(l.lotNo) like :pattern or lower(m.code) like :pattern
                   or lower(m.name) like :pattern or lower(s.name) like :pattern or lower(d.vehicleNo) like :pattern)
            """, countQuery = """
            select count(l) from MaterialLot l
            join l.material m left join l.supplier s left join l.document d
            where l.plant.id = :plantId
              and (:materialId is null or m.id = :materialId)
              and (:locationId is null or l.location.id = :locationId)
              and (:open is null or (:open = true and l.quantityLeft > 0)
                   or (:open = false and l.quantityLeft = 0 and l.cancelled = false))
              and (:pattern is null or lower(l.lotNo) like :pattern or lower(m.code) like :pattern
                   or lower(m.name) like :pattern or lower(s.name) like :pattern or lower(d.vehicleNo) like :pattern)
            """)
    Page<MaterialLot> search(@Param("plantId") Long plantId, @Param("materialId") Long materialId,
                             @Param("locationId") Long locationId, @Param("open") Boolean open,
                             @Param("pattern") String pattern, Pageable pageable);

    /** One lot with everything its row shows. */
    @Query("""
            select l from MaterialLot l
            join fetch l.plant join fetch l.material m join fetch m.unit join fetch l.location
            left join fetch l.supplier left join fetch l.document left join fetch l.line
            where l.id = :id
            """)
    Optional<MaterialLot> findWithDetailsById(@Param("id") Long id);

    /** The lots a GRN or an adjustment made. */
    @Query("""
            select l from MaterialLot l
            join fetch l.material m join fetch m.unit join fetch l.location
            left join fetch l.supplier left join fetch l.document left join fetch l.line
            where l.document.id = :documentId and l.cancelled = false
            order by l.id
            """)
    List<MaterialLot> findMadeBy(@Param("documentId") Long documentId);
}
