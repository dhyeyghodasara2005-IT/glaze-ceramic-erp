package com.ceramicerp.lab;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface LabParameterRepository extends JpaRepository<LabParameter, Long> {

    List<LabParameter> findByMaterialIdOrderBySortOrderAscIdAsc(Long materialId);

    List<LabParameter> findByMaterialIdAndActiveTrueOrderBySortOrderAscIdAsc(Long materialId);

    /** Of these materials, the ones with tests set up (they wait for the lab at the GRN). */
    @Query("select distinct p.material.id from LabParameter p where p.active = true and p.material.id in :materialIds")
    List<Long> testedOf(@Param("materialIds") Collection<Long> materialIds);

    /** Every material with tests set up, and how many. */
    @Query("select p.material.id, count(p) from LabParameter p where p.active = true group by p.material.id")
    List<Object[]> countPerMaterial();
}
