package com.ceramicerp.store;

import com.ceramicerp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** "This stock movement took 12 T from that lot." The rows that link a mill charge back to the trucks. */
@Getter
@Setter
@Entity
@Table(name = "material_lot_draws")
public class MaterialLotDraw extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false)
    private MaterialLot lot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private StockTransaction transaction;

    @Column(nullable = false, precision = 16, scale = 3)
    private BigDecimal quantity;

    /** The document was cancelled and this quantity went back to the lot. */
    @Column(nullable = false)
    private boolean reversed;
}
