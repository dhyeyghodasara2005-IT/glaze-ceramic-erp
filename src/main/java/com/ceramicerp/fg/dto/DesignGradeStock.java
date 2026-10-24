package com.ceramicerp.fg.dto;

/** All the boxes of one design and grade in a plant, whatever their pillar, batch, shade or caliber. */
public record DesignGradeStock(Long designId, Long gradeId, long boxes) {
}
