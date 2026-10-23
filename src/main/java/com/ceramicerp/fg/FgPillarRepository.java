package com.ceramicerp.fg;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FgPillarRepository extends JpaRepository<FgPillar, Long> {

    List<FgPillar> findByGodownId(Long godownId);

    /** Every pillar of every godown of a plant (the pack entry's dropdown, the stock filters). */
    @EntityGraph(attributePaths = {"godown"})
    List<FgPillar> findByGodownPlantId(Long plantId);

    @EntityGraph(attributePaths = {"godown", "godown.plant"})
    Optional<FgPillar> findWithGodownById(Long id);

    /** The pillars a pack entry's rows point at, with their godown and plant (to check they're in the right plant). */
    @EntityGraph(attributePaths = {"godown", "godown.plant"})
    List<FgPillar> findByIdIn(Collection<Long> ids);
}
