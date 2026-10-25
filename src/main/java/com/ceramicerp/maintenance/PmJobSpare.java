package com.ceramicerp.maintenance;

import com.ceramicerp.common.BaseEntity;
import com.ceramicerp.master.Material;
import com.ceramicerp.store.StoreLocation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** A spare used for a preventive job, taken from a store godown. */
@Getter
@Setter
@Entity
@Table(name = "pm_job_spares")
public class PmJobSpare extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private PmJob job;

    @Column(nullable = false)
    private int lineNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private StoreLocation location;

    @Column(nullable = false, precision = 16, scale = 3)
    private BigDecimal quantity;
}
