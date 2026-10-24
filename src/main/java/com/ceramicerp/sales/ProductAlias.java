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

/** A remembered match: the sales system's product description -> design + grade. */
@Getter
@Setter
@Entity
@Table(name = "product_aliases")
public class ProductAlias extends BaseEntity {

    /** The description tidied by ProductMatcher.key(). */
    @Column(nullable = false, length = 200)
    private String nameKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "design_id", nullable = false)
    private Design design;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grade_id", nullable = false)
    private Grade grade;
}
