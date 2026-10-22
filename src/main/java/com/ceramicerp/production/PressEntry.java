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
import java.util.ArrayList;
import java.util.List;

/** One shift of one design lot at the press and dryer. DRAFT -> POSTED -> CANCELLED. */
@Getter
@Setter
@Entity
@Table(name = "press_entries")
public class PressEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    @Column(length = 40)
    private String entryNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false)
    private ProductionLot lot;

    @Column(nullable = false)
    private LocalDate prodDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shift_id", nullable = false)
    private Shift shift;

    /** Copied from the lot, so reports by press don't need the lot. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "press_id", nullable = false)
    private Machine press;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dryer_id")
    private Machine dryer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "silo_id", nullable = false)
    private Machine silo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private DocStatus status = DocStatus.DRAFT;

    @Column(nullable = false)
    private int pressedCount;

    @Column(nullable = false)
    private int pressRejects;

    @Column(nullable = false)
    private int dryerRejects;

    @Column(precision = 6, scale = 2)
    private BigDecimal greenWeightKg;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal powderTon;

    /** Drawn beyond what the silo had on record - real production, but its origin is unknown. */
    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal untracedTon = BigDecimal.ZERO;

    @Column(precision = 6, scale = 1)
    private BigDecimal pressure;

    @Column(precision = 4, scale = 1)
    private BigDecimal cyclesPerMin;

    @Column(precision = 5, scale = 2)
    private BigDecimal thicknessMm;

    @Column(precision = 5, scale = 1)
    private BigDecimal dryerTemp;

    @Column(precision = 4, scale = 2)
    private BigDecimal dryerMoisture;

    @Column(length = 500)
    private String remarks;

    private Instant postedAt;

    @Column(length = 100)
    private String postedBy;

    private Instant cancelledAt;

    @Column(length = 100)
    private String cancelledBy;

    private String cancelReason;

    /** Filled when posted: which spray runs' powder went into these tiles. */
    @OneToMany(mappedBy = "entry", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PressEntryPowder> powders = new ArrayList<>();

    /** Tiles that reached the glaze line. */
    public int goodCount() {
        return pressedCount - pressRejects - dryerRejects;
    }
}
