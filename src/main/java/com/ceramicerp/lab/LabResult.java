package com.ceramicerp.lab;

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

/** A value the lab found, with the test as it was set up that day (a range changed later doesn't rewrite it). */
@Getter
@Setter
@Entity
@Table(name = "lab_results")
public class LabResult extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_id", nullable = false)
    private LabTest test;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parameter_id", nullable = false)
    private LabParameter parameter;

    @Column(nullable = false)
    private int sortOrder;

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

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal value;

    @Column(nullable = false)
    private boolean withinRange;
}
