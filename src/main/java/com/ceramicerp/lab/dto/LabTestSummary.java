package com.ceramicerp.lab.dto;

import com.ceramicerp.lab.LabVerdict;

import java.time.LocalDate;

public record LabTestSummary(Long id, String testNo, LocalDate testedOn, Long documentId, String docNo,
                             Long materialId, String materialCode, String materialName, String supplierName,
                             String vehicleNo, LabVerdict verdict, int outOfRange) {
}
