package com.ceramicerp.master;

import com.ceramicerp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Quality grade given at sorting: Premium, Standard, Commercial, Reject ...
 * sortOrder puts the best grade first in every list; sellable = false keeps
 * rejects/broken off sales orders.
 */
@Getter
@Setter
@Entity
@Table(name = "grades")
public class Grade extends BaseEntity {

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean sellable = true;

    @Column(nullable = false)
    private boolean active = true;
}
