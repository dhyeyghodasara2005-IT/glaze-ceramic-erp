package com.ceramicerp.fg;

import com.ceramicerp.common.BaseEntity;
import com.ceramicerp.master.Design;
import com.ceramicerp.master.Grade;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One row of a warehouse document. Which fields it uses depends on the type:
 * <ul>
 *   <li>OPENING: pillar, design, batchCode, grade, shade, caliber, boxes</li>
 *   <li>BREAKAGE: fromStock, boxes, reason</li>
 *   <li>REGRADE: fromStock, boxes, and the new grade, shade, caliber and pillar</li>
 * </ul>
 */
@Getter
@Setter
@Entity
@Table(name = "fg_document_lines")
public class FgDocumentLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private FgDocument document;

    @Column(nullable = false)
    private int lineNo;

    /** The boxes taken (breakage, regrade). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_stock_id")
    private FgStock fromStock;

    /** Where the boxes go (opening, regrade). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pillar_id")
    private FgPillar pillar;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "design_id")
    private Design design;

    @Column(length = 40)
    private String batchCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_id")
    private Grade grade;

    @Column(length = 10)
    private String shade;

    @Column(length = 10)
    private String caliber;

    @Column(nullable = false)
    private int boxes;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private BreakageReason reason;

    private String remarks;
}
