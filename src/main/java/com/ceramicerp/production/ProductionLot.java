package com.ceramicerp.production;

import com.ceramicerp.common.BaseEntity;
import com.ceramicerp.master.Design;
import com.ceramicerp.master.Machine;
import com.ceramicerp.master.Shift;
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

import java.time.Instant;
import java.time.LocalDate;

/**
 * A design lot: one run of one design on one digital printer, from the glaze line to packing.
 * The press only makes green tiles (no design yet), so a lot has no press: the presses behind it are found by
 * shift (see LotPressMatch). The lot number is what links a box in a dealer's showroom back to the shift, the silo
 * and the body batches.
 */
@Getter
@Setter
@Entity
@Table(name = "production_lots")
public class ProductionLot extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    @Column(nullable = false, length = 40)
    private String lotNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "design_id", nullable = false)
    private Design design;

    /** The printer that decides the design. It runs one design at a time: one OPEN lot per printer. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "printer_id", nullable = false)
    private Machine printer;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private LotStatus status = LotStatus.OPEN;

    @Column(nullable = false)
    private LocalDate openedDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "opened_shift_id", nullable = false)
    private Shift openedShift;

    @Column(length = 500)
    private String remarks;

    @Column(nullable = false)
    private Instant openedAt;

    @Column(length = 100)
    private String openedBy;

    private Instant closedAt;

    @Column(length = 100)
    private String closedBy;

    private Instant cancelledAt;

    @Column(length = 100)
    private String cancelledBy;

    private String cancelReason;
}
