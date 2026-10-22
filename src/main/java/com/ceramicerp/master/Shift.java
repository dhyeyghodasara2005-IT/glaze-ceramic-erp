package com.ceramicerp.master;

import com.ceramicerp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

/**
 * A working shift: A Day 08:00-20:00, B Night 20:00-08:00 ...
 * When endTime is earlier than startTime the shift crosses midnight; its work counts on the
 * production date the shift STARTED (night of 22 Oct, 02:00 on 23 Oct -> production date 22 Oct).
 */
@Getter
@Setter
@Entity
@Table(name = "shifts")
public class Shift extends BaseEntity {

    @Column(nullable = false, length = 10)
    private String code;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active = true;

    /** true for a night shift like 20:00-08:00 (also for a 24-hour shift, start == end). */
    public boolean crossesMidnight() {
        return !endTime.isAfter(startTime);
    }
}
