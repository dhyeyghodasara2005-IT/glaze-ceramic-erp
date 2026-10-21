package com.ceramicerp.master;

import com.ceramicerp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Unit of measure: KG, TON, SCM (gas), BOX ...
 * decimals = how many decimal places quantities in this unit may have (BOX 0, KG 3).
 */
@Getter
@Setter
@Entity
@Table(name = "units")
public class UnitOfMeasure extends BaseEntity {

    @Column(nullable = false, length = 10)
    private String code;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(nullable = false)
    private int decimals = 2;

    @Column(nullable = false)
    private boolean active = true;
}
