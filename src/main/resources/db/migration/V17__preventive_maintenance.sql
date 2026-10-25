-- =====================================================================
-- V17: Step 8b - preventive maintenance.
--
-- A preventive job is routine work on one machine, repeated every so many
-- days: "Press 1: clean die and punches, every 7 days". It carries the date
-- it is due next. When maintenance records it as done, the next due date
-- moves to (done date + interval), and any spares used leave the store
-- (a store issue with origin PREVENTIVE), like a breakdown in step 8a.
--
-- Each "done" record remembers the due date it fulfilled and the dates the
-- job had before, so cancelling it puts the job back exactly as it was.
-- =====================================================================

CREATE TABLE pm_tasks (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    plant_id        BIGINT          NOT NULL,
    machine_id      BIGINT          NOT NULL,
    title           VARCHAR(120)    NOT NULL,          -- "Clean die and punches"
    kind            VARCHAR(20)     NOT NULL,          -- LUBRICATION, INSPECTION, CLEANING, ADJUSTMENT, REPLACEMENT, CALIBRATION, OTHER
    every_days      INT             NOT NULL,          -- 1 = daily, 7 = weekly, 30 = monthly ...
    instructions    VARCHAR(1000),                     -- what to do and check, step by step
    needs_stop      BOOLEAN         NOT NULL DEFAULT FALSE,   -- the machine must be stopped for it
    next_due        DATE            NOT NULL,
    last_done       DATE,
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      DATETIME(6)     NOT NULL,
    updated_at      DATETIME(6)     NOT NULL,
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    version         BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_pm_task_plant FOREIGN KEY (plant_id) REFERENCES plants (id),
    CONSTRAINT fk_pm_task_machine FOREIGN KEY (machine_id) REFERENCES machines (id),
    CONSTRAINT ck_pm_task_every CHECK (every_days BETWEEN 1 AND 3650)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_pm_tasks_due ON pm_tasks (plant_id, active, next_due);
CREATE INDEX ix_pm_tasks_machine ON pm_tasks (machine_id);

CREATE TABLE pm_jobs (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    plant_id            BIGINT          NOT NULL,
    task_id             BIGINT          NOT NULL,
    job_no              VARCHAR(40)     NOT NULL,      -- UNIT-1/PM/26-27/0001
    done_on             DATE            NOT NULL,
    was_due             DATE            NOT NULL,      -- the due date this fulfilled (late = done_on - was_due)
    previous_last_done  DATE,                          -- the job's dates before, to undo a cancel exactly
    done_by             VARCHAR(150),                  -- the fitters' names
    minutes             INT,                           -- time the work took
    stop_minutes        INT,                           -- planned stop of the machine for it
    findings            VARCHAR(500),                  -- "chain loose, tightened"; "bearing noisy, order a spare"
    status              VARCHAR(20)     NOT NULL,      -- DONE / CANCELLED
    issue_document_id   BIGINT,                        -- the store issue of the spares, if any
    cancelled_at        DATETIME(6),
    cancelled_by        VARCHAR(100),
    cancel_reason       VARCHAR(255),
    created_at          DATETIME(6)     NOT NULL,
    updated_at          DATETIME(6)     NOT NULL,
    created_by          VARCHAR(100),
    updated_by          VARCHAR(100),
    version             BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_pm_jobs_no UNIQUE (plant_id, job_no),
    CONSTRAINT fk_pm_job_plant FOREIGN KEY (plant_id) REFERENCES plants (id),
    CONSTRAINT fk_pm_job_task FOREIGN KEY (task_id) REFERENCES pm_tasks (id),
    CONSTRAINT fk_pm_job_issue FOREIGN KEY (issue_document_id) REFERENCES store_documents (id),
    CONSTRAINT ck_pm_job_minutes CHECK ((minutes IS NULL OR minutes >= 0) AND (stop_minutes IS NULL OR stop_minutes >= 0))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_pm_jobs_task ON pm_jobs (task_id, status, done_on);
CREATE INDEX ix_pm_jobs_list ON pm_jobs (plant_id, status, done_on);

CREATE TABLE pm_job_spares (
    id             BIGINT          NOT NULL AUTO_INCREMENT,
    job_id         BIGINT          NOT NULL,
    line_no        INT             NOT NULL,
    material_id    BIGINT          NOT NULL,
    location_id    BIGINT          NOT NULL,
    quantity       DECIMAL(16, 3)  NOT NULL,
    created_at     DATETIME(6)     NOT NULL,
    updated_at     DATETIME(6)     NOT NULL,
    created_by     VARCHAR(100),
    updated_by     VARCHAR(100),
    version        BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_pm_spare_job FOREIGN KEY (job_id) REFERENCES pm_jobs (id) ON DELETE CASCADE,
    CONSTRAINT fk_pm_spare_material FOREIGN KEY (material_id) REFERENCES materials (id),
    CONSTRAINT fk_pm_spare_location FOREIGN KEY (location_id) REFERENCES store_locations (id),
    CONSTRAINT ck_pm_spare_qty CHECK (quantity > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
