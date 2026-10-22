package com.ceramicerp.production;

import com.ceramicerp.common.BaseEntity;
import com.ceramicerp.master.BodyRecipe;
import com.ceramicerp.master.Machine;
import com.ceramicerp.master.Shift;
import com.ceramicerp.organization.Plant;
import com.ceramicerp.store.StoreDocument;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
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

/**
 * One ball mill charge = one body batch. It carries the batch number that follows the material
 * through the spray dryer and silo to the press, so a quality problem can be traced back here.
 */
@Getter
@Setter
@Entity
@Table(name = "body_batches")
public class BodyBatch extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    /** null while DRAFT; given when the mill is charged. */
    @Column(length = 40)
    private String batchNo;

    @Column(nullable = false)
    private LocalDate prodDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shift_id", nullable = false)
    private Shift shift;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ball_mill_id", nullable = false)
    private Machine ballMill;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id")
    private BodyRecipe recipe;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private BodyBatchStatus status = BodyBatchStatus.DRAFT;

    /** Dry charge loaded into the mill, in tonnes. */
    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal chargeTon;

    @Column(precision = 12, scale = 1)
    private BigDecimal waterLitres;

    /** Plant clock time (no time zone): what the supervisor sees on the wall. */
    @Column(nullable = false)
    private LocalDateTime millingStart;

    private LocalDateTime millingEnd;

    // ---- slip test at discharge ----
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slip_tank_id")
    private Machine slipTank;

    /** Litre weight in g/L, e.g. 1700. */
    @Column(precision = 6, scale = 1)
    private BigDecimal slipDensity;

    /** Flow time in seconds. */
    @Column(precision = 6, scale = 1)
    private BigDecimal slipViscosity;

    /** % left on the sieve. */
    @Column(precision = 5, scale = 2)
    private BigDecimal slipResidue;

    /** The store issue made automatically when the mill was charged. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issue_document_id")
    private StoreDocument issueDocument;

    @Column(length = 500)
    private String remarks;

    private Instant chargedAt;

    @Column(length = 100)
    private String chargedBy;

    private Instant dischargedAt;

    @Column(length = 100)
    private String dischargedBy;

    private Instant cancelledAt;

    @Column(length = 100)
    private String cancelledBy;

    private String cancelReason;

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<BodyBatchLine> lines = new ArrayList<>();
}
