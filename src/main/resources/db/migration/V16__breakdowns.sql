-- =====================================================================
-- V16: Step 8a - the breakdown log.
--
-- A line supervisor reports a machine as stopped the moment it happens
-- (OPEN: the machine is down). The maintenance team closes it when it
-- runs again: when, what kind of fault, which part, the cause, what was
-- done, by whom, and the spares used - taken out of the store
-- automatically (a store issue with origin BREAKDOWN), like the ball-mill
-- charge in step 4a.
--
-- Downtime per machine, repair time and time between breakdowns come
-- from these rows. They are also what predictive maintenance will learn
-- from later.
-- =====================================================================

CREATE TABLE breakdowns (
    id                 BIGINT          NOT NULL AUTO_INCREMENT,
    plant_id           BIGINT          NOT NULL,
    breakdown_no       VARCHAR(40)     NOT NULL,          -- UNIT-1/BD/26-27/0001, given when reported
    machine_id         BIGINT          NOT NULL,
    stopped_at         DATETIME(6)     NOT NULL,          -- plant time, as on the shop floor
    symptom            VARCHAR(255)    NOT NULL,          -- what the supervisor saw: "press not building pressure"
    status             VARCHAR(20)     NOT NULL,          -- OPEN (down) / CLOSED (running again) / CANCELLED
    restarted_at       DATETIME(6),
    downtime_min       INT,                               -- restarted - stopped, kept for quick totals
    category           VARCHAR(20),                       -- MECHANICAL, ELECTRICAL, HYDRAULIC, ...
    failed_part        VARCHAR(100),                      -- "hydraulic pump", "roller 112"
    cause              VARCHAR(500),
    action_taken       VARCHAR(500),
    fixed_by           VARCHAR(150),                      -- the fitters' names (not everyone has a login)
    issue_document_id  BIGINT,                            -- the store issue of the spares, if any
    closed_at          DATETIME(6),
    closed_by          VARCHAR(100),
    cancelled_at       DATETIME(6),
    cancelled_by       VARCHAR(100),
    cancel_reason      VARCHAR(255),
    created_at         DATETIME(6)     NOT NULL,
    updated_at         DATETIME(6)     NOT NULL,
    created_by         VARCHAR(100),
    updated_by         VARCHAR(100),
    version            BIGINT          NOT NULL DEFAULT 0,
    -- One OPEN breakdown per machine. MySQL has no "unique where status = OPEN", so a generated column holds the
    -- machine only while the breakdown is open (NULL otherwise, and NULLs never clash in a unique key).
    open_machine_id    BIGINT AS (CASE WHEN status = 'OPEN' THEN machine_id END) STORED,
    PRIMARY KEY (id),
    CONSTRAINT uk_breakdowns_no UNIQUE (plant_id, breakdown_no),
    CONSTRAINT uk_breakdowns_open_machine UNIQUE (open_machine_id),
    CONSTRAINT fk_breakdown_plant FOREIGN KEY (plant_id) REFERENCES plants (id),
    CONSTRAINT fk_breakdown_machine FOREIGN KEY (machine_id) REFERENCES machines (id),
    CONSTRAINT fk_breakdown_issue FOREIGN KEY (issue_document_id) REFERENCES store_documents (id),
    CONSTRAINT ck_breakdown_times CHECK (restarted_at IS NULL OR restarted_at >= stopped_at),
    CONSTRAINT ck_breakdown_downtime CHECK (downtime_min IS NULL OR downtime_min >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_breakdowns_list ON breakdowns (plant_id, status, stopped_at);
CREATE INDEX ix_breakdowns_machine ON breakdowns (machine_id, stopped_at);

CREATE TABLE breakdown_spares (
    id             BIGINT          NOT NULL AUTO_INCREMENT,
    breakdown_id   BIGINT          NOT NULL,
    line_no        INT             NOT NULL,
    material_id    BIGINT          NOT NULL,
    location_id    BIGINT          NOT NULL,              -- the store godown it was taken from
    quantity       DECIMAL(16, 3)  NOT NULL,
    created_at     DATETIME(6)     NOT NULL,
    updated_at     DATETIME(6)     NOT NULL,
    created_by     VARCHAR(100),
    updated_by     VARCHAR(100),
    version        BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_bd_spare_breakdown FOREIGN KEY (breakdown_id) REFERENCES breakdowns (id) ON DELETE CASCADE,
    CONSTRAINT fk_bd_spare_material FOREIGN KEY (material_id) REFERENCES materials (id),
    CONSTRAINT fk_bd_spare_location FOREIGN KEY (location_id) REFERENCES store_locations (id),
    CONSTRAINT ck_bd_spare_qty CHECK (quantity > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- Starter roles (same rules as V9-V15: only roles this project created, never the user's own).
-- ---------------------------------------------------------------------
INSERT INTO roles (name, description, system_role, created_at, updated_at, created_by, updated_by)
SELECT 'Maintenance in-charge', 'Starter role: closes breakdowns with the cause, the fix and the spares used; sees machine downtime.', FALSE,
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'Maintenance in-charge');

INSERT INTO role_permissions (role_id, permission)
SELECT r.id, p.permission
FROM roles r
CROSS JOIN (SELECT 'MAINTENANCE_VIEW' AS permission UNION ALL SELECT 'BREAKDOWN_REPORT'
            UNION ALL SELECT 'MAINTENANCE_ENTRY') p
WHERE r.name = 'Maintenance in-charge' AND r.created_by = 'system' AND r.system_role = FALSE
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission = p.permission);

-- Every line supervisor can report their machine as down, and see the breakdowns.
INSERT INTO role_permissions (role_id, permission)
SELECT r.id, p.permission
FROM roles r
CROSS JOIN (SELECT 'MAINTENANCE_VIEW' AS permission UNION ALL SELECT 'BREAKDOWN_REPORT') p
WHERE r.name IN ('Body prep supervisor', 'Press supervisor', 'Glaze supervisor', 'Kiln supervisor', 'Polish supervisor',
                 'Packing supervisor', 'Production manager')
  AND r.created_by = 'system' AND r.system_role = FALSE
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission = p.permission);

INSERT INTO role_permissions (role_id, permission)
SELECT r.id, 'MAINTENANCE_VIEW'
FROM roles r
WHERE r.name = 'Viewer (read only)' AND r.created_by = 'system' AND r.system_role = FALSE
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission = 'MAINTENANCE_VIEW');
