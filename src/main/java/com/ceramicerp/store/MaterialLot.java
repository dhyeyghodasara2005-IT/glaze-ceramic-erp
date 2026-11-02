package com.ceramicerp.store;

import com.ceramicerp.common.BaseEntity;
import com.ceramicerp.master.Material;
import com.ceramicerp.organization.Plant;
import com.ceramicerp.party.Party;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One truck of one material in one godown (a GRN line), or stock added by an adjustment. The lots of a godown add up
 * to its stock of that material. Traces quantity only: the value of stock stays the godown's weighted average.
 */
@Getter
@Setter
@Entity
@Table(name = "material_lots")
public class MaterialLot extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private StoreLocation location;

    @Column(nullable = false, length = 50)
    private String lotNo;

    @Column(nullable = false)
    private LocalDate receivedOn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Party supplier;

    /** Empty for stock that was already there when lots began. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private StoreDocument document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "line_id")
    private StoreDocumentLine line;

    /** The stock movement that made this lot. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private StockTransaction transaction;

    @Column(nullable = false, precision = 16, scale = 3)
    private BigDecimal quantityIn;

    @Column(nullable = false, precision = 16, scale = 3)
    private BigDecimal quantityLeft;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal rate;

    @Column(nullable = false)
    private boolean cancelled;
}
