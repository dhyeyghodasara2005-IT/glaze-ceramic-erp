package com.ceramicerp.master;

import com.ceramicerp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Surface finish of a tile: Glossy, Matt, Satin, Carving ...
 * Picked when creating a design.
 */
@Getter
@Setter
@Entity
@Table(name = "finishes")
public class Finish extends BaseEntity {

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false)
    private boolean active = true;
}