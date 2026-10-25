package com.ceramicerp.maintenance.dto;

import com.ceramicerp.master.Machine;
import com.ceramicerp.master.MachineType;

/** A machine as the maintenance screens show it. */
public record MachineRef(Long id, String code, String name, MachineType type) {

    public static MachineRef of(Machine m) {
        return new MachineRef(m.getId(), m.getCode(), m.getName(), m.getType());
    }
}
