package com.ceramicerp.fg;

import com.ceramicerp.common.BaseEntity;
import com.ceramicerp.organization.Plant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A finished-goods godown of a plant. Its floor is organised by pillars (A1, A2 ...). */
@Getter
@Setter
@Entity
@Table(name = "fg_godowns")
public class FgGodown extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false)
    private boolean active = true;
}
