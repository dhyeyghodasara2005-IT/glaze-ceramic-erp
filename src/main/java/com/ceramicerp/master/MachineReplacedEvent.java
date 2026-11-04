package com.ceramicerp.master;

/**
 * Published by MachineService.replace, inside its transaction, after the new machine is saved and the old one
 * is taken out of use. Each module that keeps rows for a place in the plant listens and moves them to the new
 * machine (energy: the meters; maintenance: the preventive tasks). Refusing is done earlier, on
 * MachineReplacingEvent.
 * <p>
 * This way the masters never need to know the other modules, and a module added later only has to listen.
 */
public record MachineReplacedEvent(Long plantId, Long oldMachineId, Long newMachineId, String oldCode) {
}
