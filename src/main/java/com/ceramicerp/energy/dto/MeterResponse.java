package com.ceramicerp.energy.dto;

import com.ceramicerp.energy.Meter;
import com.ceramicerp.energy.MeterKind;
import com.ceramicerp.energy.MeterUnit;
import com.ceramicerp.energy.ReadEvery;
import com.ceramicerp.maintenance.dto.MachineRef;

/**
 * @param counter    true: read from a running total; false: the amount used is typed
 * @param everyRound true: written down every round; false: only when some was used (diesel, propane, LNG)
 */
public record MeterResponse(Long id, Long plantId, String code, String name, MeterKind kind, MeterUnit unit,
                            boolean counter, boolean everyRound, boolean main, MachineRef machine, ReadEvery readEvery,
                            int sortOrder, boolean active, Long version) {

    public static MeterResponse from(Meter m) {
        return new MeterResponse(m.getId(), m.getPlant().getId(), m.getCode(), m.getName(), m.getKind(), m.getUnit(),
                m.getKind().isCounter(), m.getKind().isEveryRound(), m.isMain(), m.getMachine() == null ? null : MachineRef.of(m.getMachine()),
                m.getReadEvery(), m.getSortOrder(), m.isActive(), m.getVersion());
    }
}
