package com.ceramicerp.organization;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Spring Data writes the SQL for these methods from their names. */
public interface CompanyRepository extends JpaRepository<Company, Long> {

    List<Company> findAllByOrderByNameAsc();

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    boolean existsByGstinIgnoreCase(String gstin);

    boolean existsByGstinIgnoreCaseAndIdNot(String gstin, Long id);
}
