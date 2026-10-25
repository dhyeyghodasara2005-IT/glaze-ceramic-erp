package com.ceramicerp.energy;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MeterRepository extends JpaRepository<Meter, Long> {

    @EntityGraph(attributePaths = {"plant", "machine"})
    List<Meter> findByPlantIdOrderBySortOrderAscCodeAsc(Long plantId);

    @EntityGraph(attributePaths = {"plant", "machine"})
    Optional<Meter> findWithDetailsById(Long id);

    /**
     * The meter, locked until the transaction ends. Saving a reading reads the one before and changes the one after,
     * so two people saving readings of the same meter at once must take turns.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Meter m where m.id = :id")
    Optional<Meter> findLockedById(@Param("id") Long id);

    boolean existsByPlantIdAndCodeIgnoreCase(Long plantId, String code);

    boolean existsByPlantIdAndCodeIgnoreCaseAndIdNot(Long plantId, String code, Long id);
}
