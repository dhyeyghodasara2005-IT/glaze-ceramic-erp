package com.ceramicerp.production.dto;

import com.ceramicerp.production.BodyBatch;
import com.ceramicerp.production.BodyBatchLine;
import com.ceramicerp.production.BodyBatchStatus;
import com.ceramicerp.production.SprayRunBatch;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** A full body batch for its screen: the charge, the slip test, and where the slip went. */
public record BodyBatchResponse(
        Long id,
        Long plantId,
        String plantCode,
        String batchNo,
        LocalDate prodDate,
        Long shiftId,
        String shiftCode,
        String shiftName,
        Long ballMillId,
        String ballMillCode,
        String ballMillName,
        Long recipeId,
        String recipeCode,
        String recipeName,
        BodyBatchStatus status,
        BigDecimal chargeTon,
        BigDecimal waterLitres,
        LocalDateTime millingStart,
        LocalDateTime millingEnd,
        Long slipTankId,
        String slipTankCode,
        String slipTankName,
        BigDecimal slipDensity,
        BigDecimal slipViscosity,
        BigDecimal slipResidue,
        Long issueDocumentId,
        String issueDocNo,
        String remarks,
        Instant chargedAt,
        String chargedBy,
        Instant dischargedAt,
        String dischargedBy,
        Instant cancelledAt,
        String cancelledBy,
        String cancelReason,
        Instant createdAt,
        String createdBy,
        Long version,
        List<Line> lines,
        /** Posted spray dryer runs that used this batch. */
        List<RunRef> sprayRuns
) {
    public record Line(Long id, int lineNo, Long materialId, String materialCode, String materialName,
                       String unitCode, int unitDecimals, Long locationId, String locationCode,
                       String locationName, BigDecimal quantity) {

        static Line from(BodyBatchLine l) {
            var m = l.getMaterial();
            var loc = l.getLocation();
            return new Line(l.getId(), l.getLineNo(), m.getId(), m.getCode(), m.getName(), m.getUnit().getCode(),
                    m.getUnit().getDecimals(), loc.getId(), loc.getCode(), loc.getName(), l.getQuantity());
        }
    }

    public record RunRef(Long runId, String runNo, LocalDate prodDate, boolean finished) {

        static RunRef from(SprayRunBatch rb) {
            return new RunRef(rb.getRun().getId(), rb.getRun().getRunNo(), rb.getRun().getProdDate(), rb.isFinished());
        }
    }

    /** Must be called inside a transaction. */
    public static BodyBatchResponse from(BodyBatch b, List<SprayRunBatch> runs) {
        var recipe = b.getRecipe();
        var tank = b.getSlipTank();
        var issue = b.getIssueDocument();
        return new BodyBatchResponse(b.getId(), b.getPlant().getId(), b.getPlant().getCode(), b.getBatchNo(),
                b.getProdDate(), b.getShift().getId(), b.getShift().getCode(), b.getShift().getName(),
                b.getBallMill().getId(), b.getBallMill().getCode(), b.getBallMill().getName(),
                recipe == null ? null : recipe.getId(), recipe == null ? null : recipe.getCode(),
                recipe == null ? null : recipe.getName(), b.getStatus(), b.getChargeTon(), b.getWaterLitres(),
                b.getMillingStart(), b.getMillingEnd(),
                tank == null ? null : tank.getId(), tank == null ? null : tank.getCode(), tank == null ? null : tank.getName(),
                b.getSlipDensity(), b.getSlipViscosity(), b.getSlipResidue(),
                issue == null ? null : issue.getId(), issue == null ? null : issue.getDocNo(),
                b.getRemarks(), b.getChargedAt(), b.getChargedBy(), b.getDischargedAt(), b.getDischargedBy(),
                b.getCancelledAt(), b.getCancelledBy(), b.getCancelReason(), b.getCreatedAt(), b.getCreatedBy(),
                b.getVersion(), b.getLines().stream().map(Line::from).toList(),
                runs.stream().map(RunRef::from).toList());
    }
}
