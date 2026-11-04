package com.ceramicerp.master;

/**
 * Published by MachineService.replace BEFORE anything changes: "this machine is about to be taken out".
 * A module that can't let it go throws a BusinessException (production: a design lot is still open on it), and the
 * replacement stops before a single row is touched.
 */
public record MachineReplacingEvent(Long plantId, Long oldMachineId, String oldCode) {
}
