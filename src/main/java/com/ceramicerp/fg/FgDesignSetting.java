package com.ceramicerp.fg;

import com.ceramicerp.common.BaseEntity;
import com.ceramicerp.master.Design;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * The warehouse's view of a design. Kept apart from the design master (which belongs to the product team):
 * the warehouse decides where boxes go, so it owns these settings.
 */
@Getter
@Setter
@Entity
@Table(name = "fg_design_settings")
public class FgDesignSetting extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "design_id", nullable = false, unique = true)
    private Design design;

    /** A best seller: its new pallets are suggested next to the loading point. */
    @Column(nullable = false)
    private boolean fastMover;
}
