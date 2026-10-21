package com.ceramicerp.organization;

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
 * A manufacturing unit. Production lines, machines, stock and dispatch
 * will all be linked to a plant in later modules.
 */
@Getter
@Setter
@Entity
@Table(name = "plants")
public class Plant extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    /** Only needed when the plant is registered in a different state from the company. */
    @Column(length = 15)
    private String gstin;

    private String addressLine;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(length = 6)
    private String pincode;

    @Column(precision = 12, scale = 2)
    private BigDecimal capacitySqmPerDay;

    @Column(nullable = false)
    private boolean active = true;
}
