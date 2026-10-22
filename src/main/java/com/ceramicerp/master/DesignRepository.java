package com.ceramicerp.master;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Database access for designs, with search + filters for the designs list. */
public interface DesignRepository extends JpaRepository<Design, Long> {

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    /**
     * One page of designs. Every filter is optional: null means "don't filter on this".
     * {@code search} must already be lower-case and wrapped in %...%.
     * @EntityGraph loads category, size and finish in the SAME query (instead of 3 extra queries per row).
     */
    @EntityGraph(attributePaths = {"category", "tileSize", "finish"})
    @Query("""
            select d from Design d
            where (:search is null or lower(d.code) like :search or lower(d.name) like :search)
              and (:categoryId is null or d.category.id = :categoryId)
              and (:sizeId is null or d.tileSize.id = :sizeId)
              and (:finishId is null or d.finish.id = :finishId)
              and (:active is null or d.active = :active)
            """)
    Page<Design> search(@Param("search") String search,
                        @Param("categoryId") Long categoryId,
                        @Param("sizeId") Long sizeId,
                        @Param("finishId") Long finishId,
                        @Param("active") Boolean active,
                        Pageable pageable);

    /** Same as findById, but with category, size and finish loaded too. */
    @EntityGraph(attributePaths = {"category", "tileSize", "finish"})
    Optional<Design> findWithDetailsById(Long id);

    /** Active designs with their size and finish - the press supervisor's "which design?" list. */
    @EntityGraph(attributePaths = {"tileSize", "finish"})
    List<Design> findByActiveTrueOrderByCodeAsc();
}
