package com.ceramicerp.fg.dto;

import com.ceramicerp.fg.FgDocType;
import com.ceramicerp.fg.FgDocument;
import com.ceramicerp.fg.FgDocumentLine;
import com.ceramicerp.store.DocStatus;

import java.time.LocalDate;

public record FgDocumentSummary(Long id, FgDocType docType, String docNo, LocalDate docDate, DocStatus status,
                                int lines, int boxes, String remarks, String createdBy) {

    public static FgDocumentSummary from(FgDocument d) {
        return new FgDocumentSummary(d.getId(), d.getDocType(), d.getDocNo(), d.getDocDate(), d.getStatus(),
                d.getLines().size(), d.getLines().stream().mapToInt(FgDocumentLine::getBoxes).sum(), d.getRemarks(),
                d.getCreatedBy());
    }
}
