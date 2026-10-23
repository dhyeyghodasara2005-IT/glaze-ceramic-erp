package com.ceramicerp.fg;

import com.ceramicerp.common.BaseEntity;
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

import java.time.LocalDate;

/** One line of the finished-goods ledger. Written once by FgStockService, never changed or deleted. */
@Getter
@Setter
@Entity
@Table(name = "fg_moves")
public class FgMove extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private FgStock stock;

    @Column(nullable = false)
    private LocalDate moveDate;

    /** + in, - out */
    @Column(nullable = false)
    private int boxes;

    /** Boxes left in the stock row right after this move. */
    @Column(nullable = false)
    private int balanceBoxes;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private FgSource source;

    @Column(nullable = false)
    private Long sourceId;

    @Column(nullable = false, length = 40)
    private String sourceNo;

    /** true for rows written when a document is cancelled. */
    @Column(nullable = false)
    private boolean reversal;
}
