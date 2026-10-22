package com.ceramicerp.master;

import com.ceramicerp.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Body recipe (body composition): which raw materials go into ONE TONNE of dry ball-mill charge.
 * A 40 T charge with "Red clay 0.300 TON/T" needs 12 TON of red clay. The supervisor can still
 * change the actual weights on the charge - the recipe only fills them in.
 */
@Getter
@Setter
@Entity
@Table(name = "body_recipes")
public class BodyRecipe extends BaseEntity {

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    private String description;

    @Column(nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<BodyRecipeLine> lines = new ArrayList<>();
}
