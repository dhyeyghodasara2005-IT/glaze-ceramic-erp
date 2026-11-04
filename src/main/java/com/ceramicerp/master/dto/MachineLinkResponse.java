package com.ceramicerp.master.dto;

import com.ceramicerp.master.MachineLink;

/** One arrow of the layout, as ids: the screens already have the machines and look them up. */
public record MachineLinkResponse(Long id, Long fromMachineId, Long toMachineId) {
    public static MachineLinkResponse from(MachineLink l) {
        return new MachineLinkResponse(l.getId(), l.getFrom().getId(), l.getTo().getId());
    }
}
