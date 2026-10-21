package com.ceramicerp.master;

import com.ceramicerp.common.BaseEntity;
import com.ceramicerp.organization.Plant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A machine or production line in one plant: "PRESS-1 · Sacmi PH 7500", "KILN-1", "SORT-1" ... */
@Getter
@Setter
@Entity
@Table(name = "machines")
public class Machine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    // Stored as text ("KILN"), not a number, so the database stays readable and reordering the enum is safe.
    // @JdbcTypeCode(VARCHAR) matches the VARCHAR column (otherwise Hibernate expects a MySQL ENUM column).
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "machine_type", nullable = false, length = 30)
    private MachineType type;

    /** e.g. 12000 with unit "sq m/day", or 40 with "tons/batch". Both optional. */
    @Column(precision = 14, scale = 2)
    private BigDecimal capacity;

    @Column(length = 30)
    private String capacityUnit;

    @Column(length = 80)
    private String make;

    @Column(length = 80)
    private String model;

    private LocalDate installedOn;

    @Column(nullable = false)
    private boolean active = true;
}
