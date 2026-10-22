package com.ceramicerp.production;

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

/** One raw material loaded into the mill, and the godown it was taken from. */
@Getter
@Setter
@Entity
@Table(name = "body_batch_lines")
public class BodyBatchLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private BodyBatch batch;

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
