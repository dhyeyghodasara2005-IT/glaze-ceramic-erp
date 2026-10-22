package com.ceramicerp.production;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ProductionLotRepository extends JpaRepository<ProductionLot, Long> {

    @EntityGraph(attributePaths = {"design", "design.tileSize", "design.finish", "press", "openedShift"})
    @Query("""
            select l from ProductionLot l
            where l.plant.id = :plantId
              and (:status is null or l.status = :status)
              and (:from is null or l.openedDate >= :from)
              and (:to is null or l.openedDate <= :to)
              and (:search is null or lower(l.lotNo) like :search
                   or lower(l.design.code) like :search or lower(l.design.name) like :search)
            """)
    Page<ProductionLot> search(@Param("plantId") Long plantId,
                               @Param("status") LotStatus status,
                               @Param("from") LocalDate from,
                               @Param("to") LocalDate to,
                               @Param("search") String search,
                               Pageable pageable);

    @EntityGraph(attributePaths = {"plant", "design", "design.tileSize", "design.finish", "press", "openedShift"})
    Optional<ProductionLot> findWithDetailsById(Long id);

    /** The lot a press line is running now (at most one). */
    @EntityGraph(attributePaths = {"design", "design.tileSize", "design.finish", "press", "openedShift"})
    Optional<ProductionLot> findFirstByPressIdAndStatus(Long pressId, LotStatus status);

    /** Lots an entry can be made for: running ones, and ones closed recently (the last shift's figures come late). */
    @EntityGraph(attributePaths = {"design", "design.tileSize", "design.finish", "press", "openedShift"})
    @Query("""
            select l from ProductionLot l
            where l.plant.id = :plantId
              and (l.status = com.ceramicerp.production.LotStatus.OPEN
                   or (l.status = com.ceramicerp.production.LotStatus.CLOSED and l.closedAt >= :closedSince))
            order by l.status desc, l.openedAt desc
            """)
    List<ProductionLot> findSelectable(@Param("plantId") Long plantId, @Param("closedSince") Instant closedSince);

    /** Lots in one state in a plant (the press home screen asks for the OPEN ones). */
    @EntityGraph(attributePaths = {"design", "design.tileSize", "design.finish", "press", "openedShift"})
    List<ProductionLot> findByPlantIdAndStatus(Long plantId, LotStatus status);
}
