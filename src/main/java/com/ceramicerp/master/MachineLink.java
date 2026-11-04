package com.ceramicerp.master;

import com.ceramicerp.common.BaseEntity;
import com.ceramicerp.organization.Plant;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One arrow of the plant's layout: "from" feeds into "to" (PRESS-1 -> DRY-1, GL-1 -> KILN-2 ...).
 * A machine may feed several and be fed by several, so any line-up can be drawn: two presses taking turns in front
 * of one kiln, one press feeding two kilns, a compensator anywhere.
 */
@Getter
@Setter
@Entity
@Table(name = "machine_links")
public class MachineLink extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_machine_id", nullable = false)
    private Machine from;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_machine_id", nullable = false)
    private Machine to;
}
