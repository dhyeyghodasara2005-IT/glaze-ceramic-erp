package com.ceramicerp.fg;

import com.ceramicerp.common.BaseEntity;
import com.ceramicerp.master.Design;
import com.ceramicerp.master.Grade;
import com.ceramicerp.organization.Plant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Boxes of one batch, grade, shade and caliber standing at one pillar. Only FgStockService changes it.
 * The lot is kept as a plain id: the finished-goods module doesn't need to know production's classes.
 */
@Getter
@Setter
@Entity
@Table(name = "fg_stock")
public class FgStock extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pillar_id", nullable = false)
    private FgPillar pillar;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "design_id", nullable = false)
    private Design design;

    /** The design lot the boxes were packed from; empty for opening stock. */
    private Long lotId;

    /** The lot number, or the code printed on older boxes. */
    @Column(nullable = false, length = 40)
    private String batchCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grade_id", nullable = false)
    private Grade grade;

    @Column(nullable = false, length = 10)
    private String shade;

    @Column(nullable = false, length = 10)
    private String caliber;

    @Column(nullable = false)
    private int boxes;
}
