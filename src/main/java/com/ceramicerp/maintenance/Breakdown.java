package com.ceramicerp.maintenance;

import com.ceramicerp.common.BaseEntity;
import com.ceramicerp.master.Machine;
import com.ceramicerp.organization.Plant;
import com.ceramicerp.store.StoreDocument;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** One machine stoppage: reported when it happens, closed by maintenance when the machine runs again. */
@Getter
@Setter
@Entity
@Table(name = "breakdowns")
public class Breakdown extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    @Column(nullable = false, length = 40)
    private String breakdownNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "machine_id", nullable = false)
    private Machine machine;

    /** Plant time, as on the shop floor. */
    @Column(nullable = false)
    private LocalDateTime stoppedAt;

    @Column(nullable = false)
    private String symptom;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private BreakdownStatus status = BreakdownStatus.OPEN;

    private LocalDateTime restartedAt;

    /** restartedAt - stoppedAt in minutes, once closed. */
    @Column(name = "downtime_min")
    private Integer downtimeMinutes;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private BreakdownCategory category;

    @Column(length = 100)
    private String failedPart;

    @Column(length = 500)
    private String cause;

    @Column(length = 500)
    private String actionTaken;

    /** The fitters' names: not everyone who fixes a machine has a login. */
    @Column(length = 150)
    private String fixedBy;

    /** The store issue of the spares used (made when the breakdown is closed with spares). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issue_document_id")
    private StoreDocument issueDocument;

    private Instant closedAt;

    @Column(length = 100)
    private String closedBy;

    private Instant cancelledAt;

    @Column(length = 100)
    private String cancelledBy;

    private String cancelReason;

    @OneToMany(mappedBy = "breakdown", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<BreakdownSpare> spares = new ArrayList<>();
}
