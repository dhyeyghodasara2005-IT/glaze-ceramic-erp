package com.ceramicerp.production;

import com.ceramicerp.common.BaseEntity;
import com.ceramicerp.fg.FgPillar;
import com.ceramicerp.master.Grade;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Boxes of one grade, shade and caliber in a pack entry - what the stickers on those boxes say. */
@Getter
@Setter
@Entity
@Table(name = "pack_entry_lines")
public class PackEntryLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entry_id", nullable = false)
    private PackEntry entry;

    @Column(nullable = false)
    private int lineNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grade_id", nullable = false)
    private Grade grade;

    @Column(nullable = false, length = 10)
    private String shade;

    @Column(nullable = false, length = 10)
    private String caliber;

    /** Where in the finished-goods godown these boxes go: the packing supervisor chooses it. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pillar_id")
    private FgPillar pillar;

    @Column(nullable = false)
    private int boxes;

    /** boxes x pieces per box, kept as it was on the day (the size master may change later). */
    @Column(nullable = false)
    private int pieces;
}
