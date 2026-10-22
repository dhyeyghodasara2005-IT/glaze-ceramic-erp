package com.ceramicerp.master.dto;

import com.ceramicerp.master.Shift;

import java.time.LocalTime;

public record ShiftResponse(Long id, String code, String name, LocalTime startTime, LocalTime endTime,
                            boolean crossesMidnight, int sortOrder, boolean active, Long version) {

    public static ShiftResponse from(Shift s) {
        return new ShiftResponse(s.getId(), s.getCode(), s.getName(), s.getStartTime(), s.getEndTime(),
                s.crossesMidnight(), s.getSortOrder(), s.isActive(), s.getVersion());
    }
}
