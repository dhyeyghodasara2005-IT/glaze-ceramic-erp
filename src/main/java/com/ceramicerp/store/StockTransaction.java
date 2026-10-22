package com.ceramicerp.store;

import com.ceramicerp.common.BaseEntity;
import com.ceramicerp.master.Material;
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

import java.math.BigDecimal;
import java.time.LocalDate;

/** One line of the stock ledger. Written once by StockService, never changed or deleted. */
@Getter
@Setter
@Entity
@Table(name = "stock_transactions")
public class StockTransaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private StoreLocation location;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @Column(nullable = false)
    private LocalDate txnDate;

    /** + in, - out */
    @Column(nullable = false, precision = 16, scale = 3)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal rate;

    /** + in, - out */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal value;

    /** Stock left in this godown right after this movement. */
    @Column(nullable = false, precision = 16, scale = 3)
    private BigDecimal balanceQty;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private StoreDocument document;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private StoreDocType docType;

    @Column(nullable = false, length = 40)
    private String docNo;

    /** true for rows written when a document is cancelled. */
    @Column(nullable = false)
    private boolean reversal;
}
