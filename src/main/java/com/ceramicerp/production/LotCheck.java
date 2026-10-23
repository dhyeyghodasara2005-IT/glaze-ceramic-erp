package com.ceramicerp.production;

import com.ceramicerp.common.BaseEntity;
import com.ceramicerp.master.Shift;
import com.ceramicerp.organization.Plant;
import com.ceramicerp.store.DocStatus;
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
import java.time.Instant;
import java.time.LocalDate;

/**
 * A quality check of a lot while its tiles cool after the kiln: bend, size and thickness on a
 * few sample tiles, the checker's verdict, and how much they expect to sort into the best grade.
 * It moves no tiles. It is final when saved (POSTED); a wrong one is CANCELLED and entered again.
 */
@Getter
@Setter
@Entity
@Table(name = "lot_checks")
public class LotCheck extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    @Column(nullable = false, length = 40)
    private String checkNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false)
    private ProductionLot lot;

    @Column(nullable = false)
    private LocalDate checkDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shift_id", nullable = false)
    private Shift shift;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private DocStatus status = DocStatus.POSTED;

    @Column(nullable = false)
    private int samples;

    /** + bulges up in the middle (convex), - dips (concave). */
    @Column(precision = 5, scale = 2)
    private BigDecimal centreBendMm;

    @Column(precision = 5, scale = 2)
    private BigDecimal edgeBendMm;

    /** One corner lifted off the flat. */
    @Column(precision = 5, scale = 2)
    private BigDecimal warpageMm;

    @Column(precision = 7, scale = 2)
    private BigDecimal lengthMm;

    @Column(precision = 7, scale = 2)
    private BigDecimal widthMm;

    @Column(precision = 5, scale = 2)
    private BigDecimal thicknessMm;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private CheckVerdict verdict;

    /** Share of the lot the checker expects in the best grade, %. */
    @Column(precision = 5, scale = 2)
    private BigDecimal expectedTopPct;

    @Column(length = 500)
    private String remarks;

    private Instant cancelledAt;

    @Column(length = 100)
    private String cancelledBy;

    private String cancelReason;
}
