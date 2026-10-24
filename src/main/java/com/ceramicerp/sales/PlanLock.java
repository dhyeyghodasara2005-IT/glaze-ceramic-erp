package com.ceramicerp.sales;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lets only one transaction per plant keep boxes for orders at a time. Two orders confirmed at the same second
 * would otherwise both see the same free boxes and both keep them. The second one waits here until the first
 * one's transaction ends, then sees what the first one kept.
 */
@Component
@RequiredArgsConstructor
class PlanLock {

    private final EntityManager em;

    @Transactional(propagation = Propagation.MANDATORY)
    void lock(Long plantId) {
        // the plant's lock row is made the first time it is needed
        em.createNativeQuery("INSERT IGNORE INTO sales_plan_locks (plant_id) VALUES (?)")
                .setParameter(1, plantId)
                .executeUpdate();
        // FOR UPDATE holds the row until this transaction commits or rolls back
        em.createNativeQuery("SELECT plant_id FROM sales_plan_locks WHERE plant_id = ? FOR UPDATE")
                .setParameter(1, plantId)
                .getSingleResult();
    }
}
