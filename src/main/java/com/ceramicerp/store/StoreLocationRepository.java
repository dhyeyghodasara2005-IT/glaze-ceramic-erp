package com.ceramicerp.store;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoreLocationRepository extends JpaRepository<StoreLocation, Long> {

    @EntityGraph(attributePaths = "plant")
    List<StoreLocation> findByPlantIdOrderByCodeAsc(Long plantId);

    @EntityGraph(attributePaths = "plant")
    Optional<StoreLocation> findWithPlantById(Long id);

    boolean existsByPlantIdAndCodeIgnoreCase(Long plantId, String code);

    boolean existsByPlantIdAndCodeIgnoreCaseAndIdNot(Long plantId, String code, Long id);
}
