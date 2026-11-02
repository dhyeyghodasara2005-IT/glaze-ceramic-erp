package com.ceramicerp.costing;

import com.ceramicerp.common.BaseEntity;
import com.ceramicerp.energy.MeterKind;
import com.ceramicerp.energy.MeterUnit;
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

/** What one unit of a fuel costs, from a date until the next rate. */
@Getter
@Setter
@Entity
@Table(name = "cost_rates")
public class CostRate extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private MeterKind kind;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private MeterUnit unit;

    @Column(nullable = false)
    private LocalDate validFrom;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal rate;

    @Column(length = 255)
    private String note;
}
