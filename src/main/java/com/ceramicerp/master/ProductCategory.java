package com.ceramicerp.master;

import com.ceramicerp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Product type: GVT, PGVT, Full Body ...
 * Carries the HSN code and GST rate that sales invoices will use for every design in it.
 */
@Getter
@Setter
@Entity
@Table(name = "product_categories")
public class ProductCategory extends BaseEntity {

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 8)
    private String hsnCode;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal gstRate;

    @Column(nullable = false)
    private boolean active = true;
}
