package com.ceramicerp.store;

import com.ceramicerp.common.BaseEntity;
import com.ceramicerp.master.Material;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** One material line of a store document. */
@Getter
@Setter
@Entity
@Table(name = "store_document_lines")
public class StoreDocumentLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private StoreDocument document;

    @Column(nullable = false)
    private int lineNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private StoreLocation location;

    /** What moves the stock: GRN accepted qty / ISSUE qty / ADJUSTMENT +- qty. */
    @Column(nullable = false, precision = 16, scale = 3)
    private BigDecimal quantity;

    @Column(precision = 16, scale = 3)
    private BigDecimal receivedQty;

    @Column(precision = 16, scale = 3)
    private BigDecimal rejectedQty;

    @Column(precision = 14, scale = 4)
    private BigDecimal rate;

    @Column(precision = 18, scale = 2)
    private BigDecimal value;

    @Column(precision = 5, scale = 2)
    private BigDecimal moisturePct;

    private String remarks;
}
