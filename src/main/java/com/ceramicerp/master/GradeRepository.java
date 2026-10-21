package com.ceramicerp.master;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Database access for grades. */
public interface GradeRepository extends JpaRepository<Grade, Long> {

    /** Best grade first; same sort order -> alphabetical. */
    List<Grade> findAllByOrderBySortOrderAscNameAsc();

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
}
