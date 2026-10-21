package com.ceramicerp.master;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UnitOfMeasureRepository extends JpaRepository<UnitOfMeasure, Long> {

    List<UnitOfMeasure> findAllByOrderByCodeAsc();

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
}
