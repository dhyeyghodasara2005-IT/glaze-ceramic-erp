package com.ceramicerp.fg.dto;

import com.ceramicerp.fg.BreakageReason;
import com.ceramicerp.fg.FgDocType;
import com.ceramicerp.fg.FgDocument;
import com.ceramicerp.fg.FgDocumentLine;
import com.ceramicerp.store.DocStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record FgDocumentResponse(
        Long id,
        Long plantId,
        String plantCode,
        FgDocType docType,
        String docNo,
        LocalDate docDate,
        DocStatus status,
        String remarks,
        List<Line> lines,
        int boxes,
        Instant postedAt,
        String postedBy,
        Instant cancelledAt,
        String cancelledBy,
        String cancelReason,
        Instant createdAt,
        String createdBy,
        Long version
) {
    /**
     * @param from where the boxes were taken (breakage, regrade), as that stock row is NOW
     */
    public record Line(Long id, int lineNo, Long fromStockId, FgStockRow from, Long pillarId, String pillarCode,
                       String godownCode, Long designId, String designName, String batchCode, Long gradeId,
                       String gradeName, String shade, String caliber, int boxes, BigDecimal sqm,
                       BreakageReason reason, String remarks) {
    }

    /** Needs the lines with their stock rows, pillars (and godowns), designs (and sizes) and grades loadable. */
    public static FgDocumentResponse from(FgDocument d) {
        List<Line> lines = d.getLines().stream().map(FgDocumentResponse::line).toList();
        return new FgDocumentResponse(d.getId(), d.getPlant().getId(), d.getPlant().getCode(), d.getDocType(),
                d.getDocNo(), d.getDocDate(), d.getStatus(), d.getRemarks(), lines,
                lines.stream().mapToInt(Line::boxes).sum(), d.getPostedAt(), d.getPostedBy(), d.getCancelledAt(),
                d.getCancelledBy(), d.getCancelReason(), d.getCreatedAt(), d.getCreatedBy(), d.getVersion());
    }

    private static Line line(FgDocumentLine l) {
        var from = l.getFromStock() == null ? null : FgStockRow.from(l.getFromStock());
        var pillar = l.getPillar();
        var design = l.getDesign() != null ? l.getDesign() : l.getFromStock() == null ? null : l.getFromStock().getDesign();
        return new Line(l.getId(), l.getLineNo(), from == null ? null : from.id(), from,
                pillar == null ? null : pillar.getId(), pillar == null ? null : pillar.getCode(),
                pillar == null ? null : pillar.getGodown().getCode(),
                l.getDesign() == null ? null : l.getDesign().getId(), l.getDesign() == null ? null : l.getDesign().getName(),
                l.getBatchCode(), l.getGrade() == null ? null : l.getGrade().getId(),
                l.getGrade() == null ? null : l.getGrade().getName(), l.getShade(), l.getCaliber(), l.getBoxes(),
                design == null ? null : FgStockRow.sqm(l.getBoxes(), design.getTileSize()), l.getReason(), l.getRemarks());
    }
}
