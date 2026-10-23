package com.ceramicerp.production.dto;

import com.ceramicerp.production.PackEntry;
import com.ceramicerp.production.PackEntryLine;
import com.ceramicerp.store.DocStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

public record PackEntryResponse(
        Long id,
        Long plantId,
        String plantCode,
        String entryNo,
        Long lotId,
        String lotNo,
        String designName,
        String sizeCode,
        String pressCode,
        Integer pcsPerBox,
        BigDecimal sqmPerTile,
        LocalDate prodDate,
        Long shiftId,
        String shiftCode,
        String shiftName,
        Long machineId,
        String machineCode,
        String machineName,
        DocStatus status,
        int tilesSorted,
        int piecesPacked,
        int boxes,
        int broken,
        BigDecimal packedSqm,
        /** broken / sorted x 100 */
        BigDecimal brokenPct,
        List<Line> lines,
        String remarks,
        Instant postedAt,
        String postedBy,
        Instant cancelledAt,
        String cancelledBy,
        String cancelReason,
        Instant createdAt,
        String createdBy,
        Long version
) {
    public record Line(Long id, int lineNo, Long gradeId, String gradeCode, String gradeName, String shade,
                       String caliber, int boxes, int pieces, BigDecimal sqm) {
    }

    /** sqm: square metres of so many pieces (the service knows the tile size). */
    public static PackEntryResponse from(PackEntry e, BigDecimal sqmPerTile, Function<Integer, BigDecimal> sqm,
                                         BigDecimal brokenPct) {
        var lot = e.getLot();
        var size = lot.getDesign().getTileSize();
        List<Line> lines = e.getLines().stream().map((PackEntryLine l) -> new Line(l.getId(), l.getLineNo(),
                l.getGrade().getId(), l.getGrade().getCode(), l.getGrade().getName(), l.getShade(), l.getCaliber(),
                l.getBoxes(), l.getPieces(), sqm.apply(l.getPieces()))).toList();
        return new PackEntryResponse(e.getId(), e.getPlant().getId(), e.getPlant().getCode(), e.getEntryNo(),
                lot.getId(), lot.getLotNo(), lot.getDesign().getName(), size.getCode(), lot.getPress().getCode(),
                size.getPcsPerBox(), sqmPerTile, e.getProdDate(), e.getShift().getId(), e.getShift().getCode(),
                e.getShift().getName(), e.getMachine().getId(), e.getMachine().getCode(), e.getMachine().getName(),
                e.getStatus(), e.getTilesSorted(), e.getPiecesPacked(), e.getBoxes(), e.getBroken(),
                sqm.apply(e.getPiecesPacked()), brokenPct, lines, e.getRemarks(), e.getPostedAt(), e.getPostedBy(),
                e.getCancelledAt(), e.getCancelledBy(), e.getCancelReason(), e.getCreatedAt(), e.getCreatedBy(),
                e.getVersion());
    }
}
