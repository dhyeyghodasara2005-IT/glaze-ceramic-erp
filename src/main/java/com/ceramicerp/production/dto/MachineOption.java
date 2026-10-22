package com.ceramicerp.production.dto;

import com.ceramicerp.master.MachineType;

/** A machine for the production dropdowns; the screen filters by type (BALL_MILL, SILO ...). */
public record MachineOption(Long id, String code, String name, MachineType type) {
}
