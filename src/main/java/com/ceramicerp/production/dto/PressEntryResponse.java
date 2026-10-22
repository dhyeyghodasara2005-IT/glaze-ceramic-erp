package com.ceramicerp.production.dto;

import com.ceramicerp.production.PressEntry;
import com.ceramicerp.production.PressEntryPowder;
import com.ceramicerp.store.DocStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PressEntryResponse(
        Long id,
        Long plantId,
        String plantCode,
        String entryNo,
        Long lotId,
        String lotNo,
        String designCode,
        String designName,
        BigDecimal sqmPerTile,
        LocalDate prodDate,
        Long shiftId,
        String shiftCode,
        String shiftName,
        Long pressId,
        String pressCode,
        Long dryerId,
        String dryerCode,
        Long siloId,
        String siloCode,
        String siloName,
        DocStatus status,
        int pressedCount,
        int pressRejects,
        int dryerRejects,
        int goodCount,
        BigDecimal goodSqm,
        BigDecimal greenWeightKg,
        BigDecimal powderTon,
        BigDecimal untracedTon,
        BigDecimal pressure,
        BigDecimal cyclesPerMin,
        BigDecimal thicknessMm,
        BigDecimal dryerTemp,
        BigDecimal dryerMoisture,
        String remarks,
        Instant postedAt,
        String postedBy,
        Instant cancelledAt,
        String cancelledBy,
        String cancelReason,
        Instant createdAt,
        String createdBy,
        Long version,
        List<PowderUse> powders
) {
    public record PowderUse(Long runId, String runNo, BigDecimal tonnes) {

        static PowderUse from(PressEntryPowder p) {
            return new PowderUse(p.getSprayRun().getId(), p.getSprayRun().getRunNo(), p.getTonnes());
        }
    }

    public static PressEntryResponse from(PressEntry e, BigDecimal sqmPerTile, BigDecimal goodSqm) {
        var lot = e.getLot();
        var dryer = e.getDryer();
        return new PressEntryResponse(e.getId(), e.getPlant().getId(), e.getPlant().getCode(), e.getEntryNo(),
                lot.getId(), lot.getLotNo(), lot.getDesign().getCode(), lot.getDesign().getName(), sqmPerTile,
                e.getProdDate(), e.getShift().getId(), e.getShift().getCode(), e.getShift().getName(),
                e.getPress().getId(), e.getPress().getCode(),
                dryer == null ? null : dryer.getId(), dryer == null ? null : dryer.getCode(),
                e.getSilo().getId(), e.getSilo().getCode(), e.getSilo().getName(), e.getStatus(),
                e.getPressedCount(), e.getPressRejects(), e.getDryerRejects(), e.goodCount(), goodSqm,
                e.getGreenWeightKg(), e.getPowderTon(), e.getUntracedTon(), e.getPressure(), e.getCyclesPerMin(),
                e.getThicknessMm(), e.getDryerTemp(), e.getDryerMoisture(), e.getRemarks(), e.getPostedAt(),
                e.getPostedBy(), e.getCancelledAt(), e.getCancelledBy(), e.getCancelReason(), e.getCreatedAt(),
                e.getCreatedBy(), e.getVersion(),
                e.getStatus() == DocStatus.POSTED ? e.getPowders().stream().map(PowderUse::from).toList() : List.of());
    }
}
