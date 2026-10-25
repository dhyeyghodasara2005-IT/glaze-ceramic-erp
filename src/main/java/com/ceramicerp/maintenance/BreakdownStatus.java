package com.ceramicerp.maintenance;

/** OPEN (the machine is down) -> CLOSED (running again). A report made by mistake is CANCELLED. */
public enum BreakdownStatus {
    OPEN,
    CLOSED,
    CANCELLED
}
