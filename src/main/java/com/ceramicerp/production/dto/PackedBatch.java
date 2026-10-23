package com.ceramicerp.production.dto;

/**
 * Boxes of a lot with the same grade, shade and caliber, over all its posted pack entries.
 * Lot + grade + shade + caliber is what a dealer orders against, so step 5 keeps stock this way.
 */
public record PackedBatch(Long gradeId, String gradeCode, String gradeName, int gradeSortOrder, String shade,
                          String caliber, long boxes, long pieces) {
}
