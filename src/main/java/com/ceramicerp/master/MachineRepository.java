package com.ceramicerp.master;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public interface MachineRepository extends JpaRepository<Machine, Long> {

    /** Process order: the order of MachineType (mill first, packing last), then code. */
    Comparator<Machine> PROCESS_ORDER = Comparator.comparing(Machine::getType).thenComparing(Machine::getCode);

    @EntityGraph(attributePaths = "plant")
    List<Machine> findByPlantId(Long plantId);

    @EntityGraph(attributePaths = "plant")
    List<Machine> findAllByOrderByPlantNameAsc();

    /**
     * Machines of one plant, in process order. The column holds the type's name, so SQL would sort it A-Z
     * (digital printer before press); Java compares enums by their declared order instead.
     */
    default List<Machine> findByPlantIdInProcessOrder(Long plantId) {
        return findByPlantId(plantId).stream().sorted(PROCESS_ORDER).toList();
    }

    /** All machines by plant, then in process order; the service filters out plants the user can't access. */
    default List<Machine> findAllInProcessOrder() {
        return findAllByOrderByPlantNameAsc().stream()
                .sorted(Comparator.comparing((Machine m) -> m.getPlant().getName()).thenComparing(PROCESS_ORDER))
                .toList();
    }

    @EntityGraph(attributePaths = "plant")
    Optional<Machine> findWithPlantById(Long id);

    /** Codes are unique within a plant: two plants may both have "PRESS-1". */
    boolean existsByPlantIdAndCodeIgnoreCase(Long plantId, String code);

    boolean existsByPlantIdAndCodeIgnoreCaseAndIdNot(Long plantId, String code, Long id);
}
