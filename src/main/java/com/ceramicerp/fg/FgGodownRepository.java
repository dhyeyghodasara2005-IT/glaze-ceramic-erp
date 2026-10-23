package com.ceramicerp.fg;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FgGodownRepository extends JpaRepository<FgGodown, Long> {

    List<FgGodown> findByPlantIdOrderByCodeAsc(Long plantId);

    @EntityGraph(attributePaths = {"plant"})
    Optional<FgGodown> findWithPlantById(Long id);

    boolean existsByPlantIdAndCodeIgnoreCase(Long plantId, String code);

    boolean existsByPlantIdAndCodeIgnoreCaseAndIdNot(Long plantId, String code, Long id);
}
