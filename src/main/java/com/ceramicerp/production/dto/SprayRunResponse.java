package com.ceramicerp.production.dto;

import com.ceramicerp.production.SprayRun;
import com.ceramicerp.production.SprayRunBatch;
import com.ceramicerp.store.DocStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record SprayRunResponse(
        Long id,
        Long plantId,
        String plantCode,
        String runNo,
        LocalDate prodDate,
        Long shiftId,
        String shiftCode,
        String shiftName,
        Long sprayDryerId,
        String sprayDryerCode,
        String sprayDryerName,
        Long siloId,
        String siloCode,
        String siloName,
        DocStatus status,
        LocalDateTime runStart,
        LocalDateTime runEnd,
        BigDecimal powderTon,
        BigDecimal powderMoisture,
        BigDecimal gasScm,
        BigDecimal inletTemp,
        BigDecimal outletTemp,
        String remarks,
        Instant postedAt,
        String postedBy,
        Instant cancelledAt,
        String cancelledBy,
        String cancelReason,
        Instant createdAt,
        String createdBy,
        Long version,
        List<BatchUse> batches
) {
    public record BatchUse(Long bodyBatchId, String batchNo, String ballMillCode, BigDecimal chargeTon,
                           String slipTankCode, LocalDateTime millingEnd, BigDecimal slipDensity,
                           BigDecimal slipResidue, boolean finished) {

        static BatchUse from(SprayRunBatch rb) {
            var b = rb.getBodyBatch();
            return new BatchUse(b.getId(), b.getBatchNo(), b.getBallMill().getCode(), b.getChargeTon(),
                    b.getSlipTank() == null ? null : b.getSlipTank().getCode(), b.getMillingEnd(),
                    b.getSlipDensity(), b.getSlipResidue(), rb.isFinished());
        }
    }

    public static SprayRunResponse from(SprayRun r) {
        return new SprayRunResponse(r.getId(), r.getPlant().getId(), r.getPlant().getCode(), r.getRunNo(),
                r.getProdDate(), r.getShift().getId(), r.getShift().getCode(), r.getShift().getName(),
                r.getSprayDryer().getId(), r.getSprayDryer().getCode(), r.getSprayDryer().getName(),
                r.getSilo().getId(), r.getSilo().getCode(), r.getSilo().getName(), r.getStatus(),
                r.getRunStart(), r.getRunEnd(), r.getPowderTon(), r.getPowderMoisture(), r.getGasScm(),
                r.getInletTemp(), r.getOutletTemp(), r.getRemarks(), r.getPostedAt(), r.getPostedBy(),
                r.getCancelledAt(), r.getCancelledBy(), r.getCancelReason(), r.getCreatedAt(), r.getCreatedBy(),
                r.getVersion(),
                // batches has no @OrderBy (it is a link table), so sort by batch number here
                r.getBatches().stream().map(BatchUse::from)
                        .sorted(Comparator.comparing(BatchUse::batchNo, Comparator.nullsLast(Comparator.naturalOrder())))
                        .toList());
    }
}
