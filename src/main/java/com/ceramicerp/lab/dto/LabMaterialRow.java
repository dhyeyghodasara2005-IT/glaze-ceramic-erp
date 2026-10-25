package com.ceramicerp.lab.dto;

/** A material and how many tests it gets on arrival (0 = it doesn't wait for the lab). */
public record LabMaterialRow(Long materialId, String code, String name, String groupCode, String groupName, String unit,
                             long tests) {
}
