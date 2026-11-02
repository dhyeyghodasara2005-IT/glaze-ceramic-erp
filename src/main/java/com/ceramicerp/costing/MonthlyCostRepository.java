package com.ceramicerp.costing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MonthlyCostRepository extends JpaRepository<MonthlyCost, Long> {

    List<MonthlyCost> findByPlantIdAndMonthStart(Long plantId, LocalDate monthStart);
}
