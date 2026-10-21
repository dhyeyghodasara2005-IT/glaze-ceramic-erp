package com.ceramicerp.organization;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlantRepository extends JpaRepository<Plant, Long> {

    /** @EntityGraph loads the company in the same query (avoids one extra query per plant). */
    @EntityGraph(attributePaths = "company")
    List<Plant> findAllByOrderByNameAsc();

    @EntityGraph(attributePaths = "company")
    List<Plant> findByCompanyIdOrderByNameAsc(Long companyId);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
}
