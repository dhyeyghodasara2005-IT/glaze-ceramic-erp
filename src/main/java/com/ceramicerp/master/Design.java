package com.ceramicerp.master;

import com.ceramicerp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * A design = one product you make and sell, e.g. "MARBLE-01, GVT, 600X1200, Glossy, 4 faces".
 * It points to one category, one size and one finish (many designs can share each of them).
 */
@Getter
@Setter
@Entity
@Table(name = "designs")
public class Design extends BaseEntity {

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    // @ManyToOne = "many designs -> one category". The column category_id holds the category's id.
    // LAZY = the category is only loaded from the database when the code actually uses it.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ProductCategory category;

    // Named tileSize (not "size") because SIZE is a reserved word in JPA's query language.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "size_id", nullable = false)
    private TileSize tileSize;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "finish_id", nullable = false)
    private Finish finish;

    /** How many print variations ("faces") the digital printer cycles through. */
    @Column(nullable = false)
    private int faces = 1;

    private LocalDate launchDate;

    private String description;

    @Column(nullable = false)
    private boolean active = true;
}
