package com.ceramicerp.fg;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FgDesignSettingRepository extends JpaRepository<FgDesignSetting, Long> {

    Optional<FgDesignSetting> findByDesignId(Long designId);

    /** Ids of the designs marked as best sellers. */
    @Query("select s.design.id from FgDesignSetting s where s.fastMover = true")
    List<Long> findFastMoverDesignIds();
}
