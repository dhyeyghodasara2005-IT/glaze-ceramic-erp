package com.ceramicerp.production.dto;

/** Boxes and pieces of one grade (e.g. packed today). */
public record GradeCount(Long gradeId, String code, String name, int sortOrder, long boxes, long pieces) {
}
