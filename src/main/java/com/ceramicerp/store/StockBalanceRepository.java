package com.ceramicerp.store;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockBalanceRepository extends JpaRepository<StockBalance, Long> {

    /**
     * Loads the balance row AND locks it (SELECT ... FOR UPDATE) until our transaction ends.
     * Two people issuing the same material at the same moment are handled one after the other,
     * so stock can never be over-issued.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from StockBalance b where b.location.id = :locationId and b.material.id = :materialId")
    Optional<StockBalance> findForUpdate(@Param("locationId") Long locationId, @Param("materialId") Long materialId);

    /** All balances of a plant with material, its group/unit, and godown loaded in one query. */
    @EntityGraph(attributePaths = {"material", "material.group", "material.unit", "location"})
    List<StockBalance> findByPlantId(Long plantId);
}
