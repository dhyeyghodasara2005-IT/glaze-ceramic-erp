package com.ceramicerp.master;

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

/**
 * Anything the factory buys and keeps in stock: clay, feldspar, frit, digital ink, boxes, gas, spares.
 * The store module (step 3) will record receipts and issues against these.
 */
@Getter
@Setter
@Entity
@Table(name = "materials")
public class Material extends BaseEntity {

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private MaterialGroup group;

    /** The unit stock is counted in (KG, TON, LTR, BOX ...). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false)
    private UnitOfMeasure unit;

    @Column(length = 8)
    private String hsnCode;

    @Column(precision = 5, scale = 2)
    private BigDecimal gstRate;

    /** Warn when stock drops below this. Optional. */
    @Column(precision = 14, scale = 3)
    private BigDecimal reorderLevel;

    private String description;

    @Column(nullable = false)
    private boolean active = true;
}
