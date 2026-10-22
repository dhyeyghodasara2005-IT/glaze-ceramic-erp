package com.ceramicerp.production;

import com.ceramicerp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * "Run SD/0004 used slip from body batch BB/0012." These rows are the trace from powder back to
 * the mill charges. finished = the batch's slip ran out during this run (the batch is then CONSUMED).
 */
@Getter
@Setter
@Entity
@Table(name = "spray_run_batches")
public class SprayRunBatch extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private SprayRun run;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "body_batch_id", nullable = false)
    private BodyBatch bodyBatch;

    @Column(nullable = false)
    private boolean finished;
}
