package com.ceramicerp.master;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Database access for finishes. Spring Data writes the SQL from the method names. */
public interface FinishRepository extends JpaRepository<Finish, Long> {

    /** All finishes in alphabetical order (Carving, Glossy, Matt, Satin). */
    List<Finish> findAllByOrderByNameAsc();

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
}