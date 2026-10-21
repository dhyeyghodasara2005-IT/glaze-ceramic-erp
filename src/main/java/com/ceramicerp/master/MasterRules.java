package com.ceramicerp.master;

import com.ceramicerp.common.BaseEntity;
import com.ceramicerp.common.exception.BusinessException;

/** Rules shared by several master services. */
final class MasterRules {

    private MasterRules() {
    }

    /**
     * Picking an inactive master is refused - unless the record already uses it
     * (editing an old design/material whose size/unit was retired must still be possible).
     *
     * @param active  is the chosen master active?
     * @param current what the record points to now (null for a new record)
     * @param chosen  what the user picked
     */
    static void requireActiveIfChanged(boolean active, BaseEntity current, BaseEntity chosen, String what, String code) {
        boolean unchanged = current != null && current.getId().equals(chosen.getId());
        if (!active && !unchanged) {
            throw new BusinessException(what + " " + code + " is inactive. Choose an active one.");
        }
    }
}
