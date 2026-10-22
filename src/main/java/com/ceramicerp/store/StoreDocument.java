package com.ceramicerp.store;

import com.ceramicerp.common.BaseEntity;
import com.ceramicerp.master.Machine;
import com.ceramicerp.organization.Plant;
import com.ceramicerp.party.Party;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** A GRN, material issue or stock adjustment (header). The lines are in {@link #lines}. */
@Getter
@Setter
@Entity
@Table(name = "store_documents")
public class StoreDocument extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private StoreDocType docType;

    /** null while DRAFT; given when posted. */
    @Column(length = 40)
    private String docNo;

    @Column(nullable = false)
    private LocalDate docDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private DocStatus status = DocStatus.DRAFT;

    /** null = typed in the store. Otherwise the module that made it (e.g. "BODY_BATCH"); only that module may change it. */
    @Column(length = 20)
    private String origin;

    // ---- GRN ----
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Party supplier;

    @Column(length = 40)
    private String supplierInvoiceNo;

    private LocalDate supplierInvoiceDate;

    @Column(length = 20)
    private String vehicleNo;

    @Column(length = 30)
    private String weighbridgeSlipNo;

    // ---- ISSUE ----
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "machine_id")
    private Machine machine;

    @Column(length = 100)
    private String issuedTo;

    // ---- ADJUSTMENT ----
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private AdjustmentReason reason;

    @Column(length = 500)
    private String remarks;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal totalValue = BigDecimal.ZERO;

    private Instant postedAt;

    @Column(length = 100)
    private String postedBy;

    private Instant cancelledAt;

    @Column(length = 100)
    private String cancelledBy;

    private String cancelReason;

    /**
     * mappedBy = the line's "document" field owns the link (document_id column).
     * cascade ALL = saving/deleting the document saves/deletes its lines too.
     * orphanRemoval = a line removed from this list is deleted from the database.
     */
    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<StoreDocumentLine> lines = new ArrayList<>();
}
