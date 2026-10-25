package com.ceramicerp.lab;

import com.ceramicerp.common.BaseEntity;
import com.ceramicerp.master.Material;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** One test a material gets when it arrives (moisture, residue ...), and the range the plant accepts, if it has one. */
@Getter
@Setter
@Entity
@Table(name = "lab_parameters")
public class LabParameter extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @Column(nullable = false, length = 10)
    private String code;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(length = 15)
    private String unit;

    @Column(precision = 12, scale = 3)
    private BigDecimal minValue;

    @Column(precision = 12, scale = 3)
    private BigDecimal maxValue;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active = true;

    /** Inside the range (an open end allows anything on that side). */
    boolean accepts(BigDecimal value) {
        return (minValue == null || value.compareTo(minValue) >= 0) && (maxValue == null || value.compareTo(maxValue) <= 0);
    }
}
