package com.ceramicerp.costing;

import com.ceramicerp.energy.MeterKind;
import com.ceramicerp.energy.MeterUnit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CostRateRepository extends JpaRepository<CostRate, Long> {

    List<CostRate> findByPlantIdOrderByKindAscUnitAscValidFromDesc(Long plantId);

    @EntityGraph(attributePaths = "plant")
    Optional<CostRate> findWithPlantById(Long id);

    boolean existsByPlantIdAndKindAndUnitAndValidFrom(Long plantId, MeterKind kind, MeterUnit unit, LocalDate validFrom);

    boolean existsByPlantIdAndKindAndUnitAndValidFromAndIdNot(Long plantId, MeterKind kind, MeterUnit unit, LocalDate validFrom, Long id);
}
