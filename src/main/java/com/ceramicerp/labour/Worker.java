package com.ceramicerp.labour;

import com.ceramicerp.common.BaseEntity;
import com.ceramicerp.organization.Plant;
import com.ceramicerp.party.Party;
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

/** Someone who works in the plant: under which contractor, where, how attendance is taken, what a day costs. */
@Getter
@Setter
@Entity
@Table(name = "workers")
public class Worker extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    /** Null = on the plant's own rolls. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contractor_id")
    private Party contractor;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Department department;

    @Column(length = 60)
    private String job;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 12)
    private PunchMethod punch;

    /** The worker's number in the fingerprint machine. */
    @Column(length = 20)
    private String deviceId;

    @Column(precision = 10, scale = 2)
    private BigDecimal dayRate;

    @Column(precision = 10, scale = 2)
    private BigDecimal otRate;

    @Column(length = 20)
    private String phone;

    private LocalDate joinedOn;

    @Column(nullable = false)
    private boolean active = true;
}
