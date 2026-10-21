package com.ceramicerp.master;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Database access for product categories. */
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

    List<ProductCategory> findAllByOrderByNameAsc();

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
}
