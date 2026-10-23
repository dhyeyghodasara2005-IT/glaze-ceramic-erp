package com.ceramicerp.fg;

import com.ceramicerp.common.BaseEntity;
import com.ceramicerp.organization.Plant;
import com.ceramicerp.store.DocStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** A warehouse document: opening stock, breakage or regrading (the type). */
@Getter
@Setter
@Entity
@Table(name = "fg_documents")
public class FgDocument extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private FgDocType docType;

    @Column(length = 40)
    private String docNo;

    @Column(nullable = false)
    private LocalDate docDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private DocStatus status = DocStatus.DRAFT;

    @Column(length = 500)
    private String remarks;

    private Instant postedAt;

    @Column(length = 100)
    private String postedBy;

    private Instant cancelledAt;

    @Column(length = 100)
    private String cancelledBy;

    private String cancelReason;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<FgDocumentLine> lines = new ArrayList<>();
}
