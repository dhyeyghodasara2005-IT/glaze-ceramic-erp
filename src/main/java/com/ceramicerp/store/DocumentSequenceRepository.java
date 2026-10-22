package com.ceramicerp.store;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, Long> {

    /**
     * SELECT ... FOR UPDATE: the row stays locked until our transaction ends, so a second person
     * posting at the same moment waits instead of getting the same number.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DocumentSequence> findByPlantIdAndDocTypeAndFinYear(Long plantId, String docType, String finYear);
}
