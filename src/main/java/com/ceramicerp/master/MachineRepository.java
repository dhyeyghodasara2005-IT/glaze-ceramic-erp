package com.ceramicerp.master;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MachineRepository extends JpaRepository<Machine, Long> {

    /** Machines of one plant, in process order (type) then code. */
    @EntityGraph(attributePaths = "plant")
    List<Machine> findByPlantIdOrderByTypeAscCodeAsc(Long plantId);

    /** All machines; the service filters out plants the user can't access. */
    @EntityGraph(attributePaths = "plant")
    List<Machine> findAllByOrderByPlantNameAscTypeAscCodeAsc();

    @EntityGraph(attributePaths = "plant")
    Optional<Machine> findWithPlantById(Long id);

    /** Codes are unique within a plant: two plants may both have "PRESS-1". */
    boolean existsByPlantIdAndCodeIgnoreCase(Long plantId, String code);

    boolean existsByPlantIdAndCodeIgnoreCaseAndIdNot(Long plantId, String code, Long id);
}
