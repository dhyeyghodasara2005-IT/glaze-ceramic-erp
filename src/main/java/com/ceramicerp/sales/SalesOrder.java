package com.ceramicerp.sales;

import com.ceramicerp.common.BaseEntity;
import com.ceramicerp.organization.Plant;
import com.ceramicerp.party.Party;
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
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** One loading slip from the sales system, kept as a sales order. */
@Getter
@Setter
@Entity
@Table(name = "sales_orders")
public class SalesOrder extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    /** Loading Slip No. - unique per plant, so the same slip is never entered twice. */
    @Column(nullable = false, length = 30)
    private String slipNo;

    @Column(nullable = false)
    private LocalDate slipDate;

    /** The sales system's Sales Order No. */
    @Column(length = 30)
    private String orderRef;

    private LocalDate orderDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private SalesOrderType orderType;

    /** Who is billed: the dealer, or the jobwork principal. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "party_id", nullable = false)
    private Party party;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transporter_id")
    private Party transporter;

    /** Truck details, noted at loading (step 6c). */
    @Column(length = 20)
    private String truckNo;

    @Column(length = 100)
    private String driverName;

    @Column(length = 15)
    private String driverMobile;

    /** The transporter's lorry receipt no. */
    @Column(length = 30)
    private String lrNo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private SalesOrderStatus status = SalesOrderStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private OrderSource source = OrderSource.TYPED;

    /** "Total No. of Boxes" printed on the slip, to cross-check the lines. */
    private Integer slipBoxes;

    /** "Total Weight" printed on the slip. */
    @Column(precision = 10, scale = 1)
    private BigDecimal slipWeightKg;

    @Column(length = 500)
    private String remarks;

    private Instant confirmedAt;

    @Column(length = 100)
    private String confirmedBy;

    /** When the boxes were last picked for this order. */
    private Instant plannedAt;

    /** The dispatch / gate pass no., given when the loading is first confirmed (kept if it is undone and redone). */
    @Column(length = 40)
    private String dispatchNo;

    private LocalDate dispatchDate;

    private Instant dispatchedAt;

    @Column(length = 100)
    private String dispatchedBy;

    /** The last "undo dispatch", if any. */
    private Instant undoneAt;

    @Column(length = 100)
    private String undoneBy;

    private String undoReason;

    private Instant cancelledAt;

    @Column(length = 100)
    private String cancelledBy;

    private String cancelReason;

    // @BatchSize: the order list loads the lines of up to 50 orders in one query, not one query per order.
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    @BatchSize(size = 50)
    private List<SalesOrderLine> lines = new ArrayList<>();

    /** The loading plan (empty while a draft). */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<SalesOrderPick> picks = new ArrayList<>();
}
