package com.ceramicerp.production;

import com.ceramicerp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** "Press entry PR/0007 used 12 T of powder from spray run SD/0003." */
@Getter
@Setter
@Entity
@Table(name = "press_entry_powders")
public class PressEntryPowder extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entry_id", nullable = false)
    private PressEntry entry;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spray_run_id", nullable = false)
    private SprayRun sprayRun;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal tonnes;
}
