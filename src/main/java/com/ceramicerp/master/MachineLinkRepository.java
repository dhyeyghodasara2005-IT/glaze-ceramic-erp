package com.ceramicerp.master;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MachineLinkRepository extends JpaRepository<MachineLink, Long> {

    /** Every arrow of one plant's layout. Only ids are read, so no machine is loaded. */
    List<MachineLink> findByPlantId(Long plantId);

    List<MachineLink> findByFromId(Long machineId);

    /** The arrows into or out of one machine. */
    @Query("select l from MachineLink l where l.from.id = :machineId or l.to.id = :machineId")
    List<MachineLink> findTouching(@Param("machineId") Long machineId);

    boolean existsByFromIdOrToId(Long fromId, Long toId);
}
