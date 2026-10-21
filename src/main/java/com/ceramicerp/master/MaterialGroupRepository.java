package com.ceramicerp.master;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaterialGroupRepository extends JpaRepository<MaterialGroup, Long> {

    List<MaterialGroup> findAllByOrderByNameAsc();

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
}
