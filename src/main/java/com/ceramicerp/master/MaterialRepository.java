package com.ceramicerp.master;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Database access for materials, with search + filters (same idea as DesignRepository). */
public interface MaterialRepository extends JpaRepository<Material, Long> {

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    @EntityGraph(attributePaths = {"group", "unit"})
    @Query("""
            select m from Material m
            where (:search is null or lower(m.code) like :search or lower(m.name) like :search)
              and (:groupId is null or m.group.id = :groupId)
              and (:active is null or m.active = :active)
            """)
    Page<Material> search(@Param("search") String search,
                          @Param("groupId") Long groupId,
                          @Param("active") Boolean active,
                          Pageable pageable);

    @EntityGraph(attributePaths = {"group", "unit"})
    Optional<Material> findWithDetailsById(Long id);

    /** Every active material with its group and unit - for dropdowns and the stock screen. */
    @EntityGraph(attributePaths = {"group", "unit"})
    List<Material> findByActiveTrueOrderByCodeAsc();
}
