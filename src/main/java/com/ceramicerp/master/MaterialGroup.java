package com.ceramicerp.master;

import com.ceramicerp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Group of purchased materials: Body, Glaze & frit, Digital inks, Packing, Fuel ... */
@Getter
@Setter
@Entity
@Table(name = "material_groups")
public class MaterialGroup extends BaseEntity {

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false)
    private boolean active = true;
}
