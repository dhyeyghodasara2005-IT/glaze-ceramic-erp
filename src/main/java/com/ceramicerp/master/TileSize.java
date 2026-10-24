package com.ceramicerp.master;

import com.ceramicerp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A tile size, e.g. 600 x 1200 mm, 9 mm thick, 2 pieces per box.
 * sqmPerBox is calculated by the service from the other fields - users never type it.
 */
@Getter
@Setter
@Entity
@Table(name = "sizes")
public class TileSize extends BaseEntity {

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false)
    private Integer lengthMm;

    @Column(nullable = false)
    private Integer widthMm;

    @Column(nullable = false, precision = 4, scale = 1)
    private BigDecimal thicknessMm;

    @Column(nullable = false)
    private Integer pcsPerBox;

    @Column(nullable = false, precision = 8, scale = 4)
    private BigDecimal sqmPerBox;

    @Column(precision = 6, scale = 2)
    private BigDecimal boxWeightKg;

    private Integer boxesPerPallet;

    @Column(nullable = false)
    private boolean active = true;

    /**
     * Square metres of one tile: 600 x 1200 mm -> 0.72. Falls back to sq m per box / pieces per box for a size
     * entered without length and width. null if neither is known. (Not a getter, so it is not a column.)
     */
    public BigDecimal sqmPerTile() {
        if (lengthMm != null && widthMm != null) {
            return BigDecimal.valueOf((long) lengthMm * widthMm).divide(BigDecimal.valueOf(1_000_000), 4, RoundingMode.HALF_UP);
        }
        if (sqmPerBox != null && pcsPerBox != null && pcsPerBox > 0) {
            return sqmPerBox.divide(BigDecimal.valueOf(pcsPerBox), 4, RoundingMode.HALF_UP);
        }
        return null;
    }
}