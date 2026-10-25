package com.ceramicerp.maintenance;

import com.ceramicerp.common.BaseEntity;
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
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** A preventive job done once: when, by whom, what was found, and the spares used. */
@Getter
@Setter
@Entity
@Table(name = "pm_jobs")
public class PmJob extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private PmTask task;

    @Column(nullable = false, length = 40)
    private String jobNo;

    @Column(nullable = false)
    private LocalDate doneOn;

    /** The due date this fulfilled: done late when doneOn is after it. Cancelling puts it back as the next due. */
    @Column(nullable = false)
    private LocalDate wasDue;

    /** The job's last-done date before this one, restored on cancel. */
    private LocalDate previousLastDone;

    @Column(length = 150)
    private String doneBy;

    private Integer minutes;

    /** How long the machine was stopped for it (planned downtime). */
    private Integer stopMinutes;

    @Column(length = 500)
    private String findings;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PmJobStatus status = PmJobStatus.DONE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issue_document_id")
    private StoreDocument issueDocument;

    private Instant cancelledAt;

    @Column(length = 100)
    private String cancelledBy;

    private String cancelReason;

    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<PmJobSpare> spares = new ArrayList<>();

    /** Days after the due date it was done (0 = on time or early). */
    public int daysLate() {
        return (int) Math.max(0, ChronoUnit.DAYS.between(wasDue, doneOn));
    }
}
