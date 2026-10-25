package com.ceramicerp.maintenance;

import com.ceramicerp.common.BaseEntity;
import com.ceramicerp.master.Machine;
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

import java.time.LocalDate;

/** Routine work on one machine, every so many days, with the date it is due next. */
@Getter
@Setter
@Entity
@Table(name = "pm_tasks")
public class PmTask extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "machine_id", nullable = false)
    private Machine machine;

    @Column(nullable = false, length = 120)
    private String title;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PmKind kind;

    @Column(nullable = false)
    private int everyDays;

    @Column(length = 1000)
    private String instructions;

    /** The machine must be stopped for it. */
    @Column(nullable = false)
    private boolean needsStop;

    @Column(nullable = false)
    private LocalDate nextDue;

    private LocalDate lastDone;

    @Column(nullable = false)
    private boolean active = true;
}
