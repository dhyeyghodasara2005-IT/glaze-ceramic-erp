package com.ceramicerp.master.dto;

import com.ceramicerp.master.Machine;
import com.ceramicerp.master.MachineType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MachineResponse(
        Long id,
        Long plantId,
        String plantCode,
        String plantName,
        String code,
        String name,
        MachineType type,
        BigDecimal capacity,
        String capacityUnit,
        String make,
        String model,
        LocalDate installedOn,
        boolean active,
        Long version
) {
    public static MachineResponse from(Machine m) {
        var p = m.getPlant();
        return new MachineResponse(m.getId(), p.getId(), p.getCode(), p.getName(), m.getCode(), m.getName(),
                m.getType(), m.getCapacity(), m.getCapacityUnit(), m.getMake(), m.getModel(), m.getInstalledOn(),
                m.isActive(), m.getVersion());
    }
}
