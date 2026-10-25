package com.ceramicerp.lab.dto;

import com.ceramicerp.lab.LabVerdict;

/**
 * Where one material of a GRN stands with the lab, for the GRN page.
 *
 * @param testId null while it waits for the lab
 */
public record GrnLabStatus(Long materialId, String materialName, Long testId, String testNo, LabVerdict verdict) {
}
