package com.ceramicerp.production;

import com.ceramicerp.common.BaseEntity;
import com.ceramicerp.master.Machine;
import com.ceramicerp.master.Shift;
import com.ceramicerp.organization.Plant;
import com.ceramicerp.store.DocStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** A spray dryer run: slip from body batches becomes powder in a silo. DRAFT -> POSTED -> CANCELLED. */
@Getter
@Setter
@Entity
@Table(name = "spray_runs")
public class SprayRun extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    @Column(length = 40)
    private String runNo;

    @Column(nullable = false)
    private LocalDate prodDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shift_id", nullable = false)
    private Shift shift;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spray_dryer_id", nullable = false)
    private Machine sprayDryer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "silo_id", nullable = false)
    private Machine silo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private DocStatus status = DocStatus.DRAFT;

    @Column(nullable = false)
    private LocalDateTime runStart;

    private LocalDateTime runEnd;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal powderTon;

    /** Powder from this run still in the silo. Set to powderTon when posted; press entries draw it down. */
    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal powderLeft = BigDecimal.ZERO;

    @Column(precision = 5, scale = 2)
    private BigDecimal powderMoisture;

    @Column(precision = 12, scale = 2)
    private BigDecimal gasScm;

    @Column(precision = 6, scale = 1)
    private BigDecimal inletTemp;

    @Column(precision = 6, scale = 1)
    private BigDecimal outletTemp;

    @Column(length = 500)
    private String remarks;

    private Instant postedAt;

    @Column(length = 100)
    private String postedBy;

    private Instant cancelledAt;

    @Column(length = 100)
    private String cancelledBy;

    private String cancelReason;

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SprayRunBatch> batches = new ArrayList<>();
}
