package com.ceramicerp.energy;

import com.ceramicerp.common.BaseEntity;
import com.ceramicerp.master.Shift;
import com.ceramicerp.organization.Plant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One reading of a meter in one round (a day, or a shift), and what was used since the reading before. */
@Getter
@Setter
@Entity
@Table(name = "meter_readings")
public class MeterReading extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meter_id", nullable = false)
    private Meter meter;

    /**
     * The production day it closes: a shift's production date, or for a once-a-day meter the day before the morning
     * it was read on.
     */
    @Column(nullable = false)
    private LocalDate readOn;

    /** Null for once-a-day meters. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id")
    private Shift shift;

    /** The number on the meter (counters only). */
    @Column(precision = 16, scale = 3)
    private BigDecimal reading;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal used;

    /** The meter was changed or started again from 0: this reading is counted from 0. */
    @Column(nullable = false)
    private boolean restarted;

    @Column(length = 255)
    private String note;

    /** Where it stands among the meter's readings: by date, then shift order. */
    int order() {
        return shift == null ? -1 : shift.getSortOrder();
    }
}
