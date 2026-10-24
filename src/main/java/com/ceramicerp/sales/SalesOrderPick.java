package com.ceramicerp.sales;

import com.ceramicerp.common.BaseEntity;
import com.ceramicerp.fg.FgStock;
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

/**
 * One row of the loading plan: this many boxes of this order line come from this stock row (one pillar, one batch,
 * shade and caliber). While the order is CONFIRMED the boxes are kept for it.
 */
@Getter
@Setter
@Entity
@Table(name = "sales_order_picks")
public class SalesOrderPick extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private SalesOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "line_id", nullable = false)
    private SalesOrderLine line;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private FgStock stock;

    @Column(nullable = false)
    private int plannedBoxes;

    /** What the loader actually loaded from here; null until ticked. */
    private Integer loadedBoxes;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PickReason reason;

    private Instant loadedAt;

    @Column(length = 100)
    private String loadedBy;

    /** The boxes this pick keeps from other orders: what was loaded once ticked, else what was planned. */
    public int keptBoxes() {
        return loadedBoxes != null ? loadedBoxes : plannedBoxes;
    }
}
