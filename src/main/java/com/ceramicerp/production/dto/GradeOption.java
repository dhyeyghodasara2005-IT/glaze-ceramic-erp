package com.ceramicerp.production.dto;

/** A sellable grade for the pack entry's dropdown, best first. */
public record GradeOption(Long id, String code, String name, int sortOrder) {
}
