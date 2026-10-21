package com.ceramicerp.master;

import com.ceramicerp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

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
}