package com.ceramicerp.production;

import com.ceramicerp.common.BaseEntity;
import com.ceramicerp.master.Machine;
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

/** One shift of one design lot at the glaze line (stage GLAZE), the kiln (KILN) or the polishing line (POLISH). */
@Getter
@Setter
@Entity
@Table(name = "line_entries")
public class LineEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    @Column(length = 40)
    private String entryNo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private LineStage stage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false)
    private ProductionLot lot;

    @Column(nullable = false)
    private LocalDate prodDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shift_id", nullable = false)
    private Shift shift;

    /** The glaze line, the kiln or the polishing line. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "machine_id", nullable = false)
    private Machine machine;

    /** Digital printer - glaze stage only. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "printer_id")
    private Machine printer;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private DocStatus status = DocStatus.DRAFT;

    @Column(nullable = false)
    private int tilesIn;

    @Column(nullable = false)
    private int rejects;

    // ---- glaze & print ----
    @Column(precision = 7, scale = 1)
    private BigDecimal engobeGpt;

    @Column(precision = 7, scale = 1)
    private BigDecimal glazeGpt;

    @Column(precision = 6, scale = 1)
    private BigDecimal glazeDensity;

    @Column(precision = 8, scale = 2)
    private BigDecimal inkLitres;

    // ---- kiln ----
    @Column(precision = 6, scale = 1)
    private BigDecimal maxTemp;

    @Column(precision = 5, scale = 1)
    private BigDecimal cycleMin;

    @Column(precision = 12, scale = 2)
    private BigDecimal gasScm;

    // ---- polishing ----
    @Column(precision = 12, scale = 2)
    private BigDecimal powerKwh;

    @Column(length = 500)
    private String remarks;

    private Instant postedAt;

    @Column(length = 100)
    private String postedBy;

    private Instant cancelledAt;

    @Column(length = 100)
    private String cancelledBy;

    private String cancelReason;

    /** Tiles that went on to the next stage. */
    public int tilesOut() {
        return tilesIn - rejects;
    }
}
