package com.ceramicerp.sales;

import com.ceramicerp.common.BaseEntity;
import com.ceramicerp.master.Design;
import com.ceramicerp.master.Grade;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** One product line of a loading slip: the slip's own description, and the design and grade it was matched to. */
@Getter
@Setter
@Entity
@Table(name = "sales_order_lines")
public class SalesOrderLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private SalesOrder order;

    @Column(nullable = false)
    private int lineNo;

    /** Exactly as printed on the slip. */
    @Column(nullable = false, length = 200)
    private String description;

    /** Matched design (needed to confirm). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "design_id")
    private Design design;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_id")
    private Grade grade;

    @Column(nullable = false)
    private int boxes;

    /** Printed MRP per box - not the bill rate. */
    @Column(precision = 10, scale = 2)
    private BigDecimal mrpBox;

    @Column(precision = 10, scale = 2)
    private BigDecimal mrpPiece;

    /** The slip's Batch No. (production batch), for reference only: any batch may be loaded. */
    @Column(length = 30)
    private String batchRef;
}
