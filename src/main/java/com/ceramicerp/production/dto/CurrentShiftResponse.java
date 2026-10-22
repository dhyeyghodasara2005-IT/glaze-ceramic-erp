package com.ceramicerp.production.dto;

import java.time.LocalDate;

/** The shift running now and the production date it belongs to (forms start with these). */
public record CurrentShiftResponse(Long shiftId, String code, String name, LocalDate prodDate) {
}
