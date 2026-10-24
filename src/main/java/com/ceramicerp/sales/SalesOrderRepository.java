package com.ceramicerp.sales;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

    @EntityGraph(attributePaths = {"party"})
    @Query("""
            select o from SalesOrder o
            where o.plant.id = :plantId
              and (:status is null or o.status = :status)
              and (:type is null or o.orderType = :type)
              and (:from is null or o.slipDate >= :from)
              and (:to is null or o.slipDate <= :to)
              and (:search is null or lower(o.slipNo) like :search or lower(o.orderRef) like :search
                   or lower(o.party.name) like :search)
            """)
    Page<SalesOrder> search(@Param("plantId") Long plantId,
                            @Param("status") SalesOrderStatus status,
                            @Param("type") SalesOrderType type,
                            @Param("from") LocalDate from,
                            @Param("to") LocalDate to,
                            @Param("search") String search,
                            Pageable pageable);

    @EntityGraph(attributePaths = {"plant", "party", "transporter"})
    Optional<SalesOrder> findWithDetailsById(Long id);

    boolean existsByPlantIdAndPartyIdAndSlipNo(Long plantId, Long partyId, String slipNo);

    boolean existsByPlantIdAndPartyIdAndSlipNoAndIdNot(Long plantId, Long partyId, String slipNo, Long id);
}
