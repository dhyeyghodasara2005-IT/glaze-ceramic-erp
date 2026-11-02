-- =====================================================================
-- V20: Step 9a - labour: workers, daily attendance, the month per contractor.
--
-- From the plant (25 Oct 2026): workers are paid monthly through labour
-- contractors; attendance is by fingerprint for those who can read and
-- write, and on cards signed by the supervisor for the others.
--
--   labour contractors   a new kind of party (PartyType.CONTRACTOR, LBC-0001)
--   workers              who works here, under which contractor, in which department,
--                        how their attendance is taken, and their day rate
--   attendance           one row per worker per day: present, half day, absent or
--                        leave, overtime hours, and who marked it
--
-- How a contractor is paid is still an open question (sheet F, question 30):
-- until then a contractor's month is man-days x each worker's day rate.
-- =====================================================================

CREATE TABLE workers (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    plant_id        BIGINT          NOT NULL,
    code            VARCHAR(20)     NOT NULL,          -- W-0001, or the number on the worker's card
    name            VARCHAR(100)    NOT NULL,
    contractor_id   BIGINT,                            -- null = on the plant's own rolls
    department      VARCHAR(20)     NOT NULL,          -- PRESS, KILN, PACKING ... (Department.java)
    job             VARCHAR(60),                       -- operator, helper, loader ...
    punch           VARCHAR(12)     NOT NULL,          -- FINGERPRINT / CARD: how attendance is taken
    device_id       VARCHAR(20),                       -- the worker's number in the fingerprint machine
    day_rate        DECIMAL(10, 2),                    -- what a day of this worker costs
    ot_rate         DECIMAL(10, 2),                    -- what an hour of overtime costs
    phone           VARCHAR(20),
    joined_on       DATE,
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      DATETIME(6)     NOT NULL,
    updated_at      DATETIME(6)     NOT NULL,
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    version         BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_workers_code UNIQUE (plant_id, code),
    CONSTRAINT uk_workers_device UNIQUE (plant_id, device_id),          -- NULLs never clash
    CONSTRAINT fk_worker_plant FOREIGN KEY (plant_id) REFERENCES plants (id),
    CONSTRAINT fk_worker_contractor FOREIGN KEY (contractor_id) REFERENCES parties (id),
    CONSTRAINT ck_worker_rates CHECK ((day_rate IS NULL OR day_rate >= 0) AND (ot_rate IS NULL OR ot_rate >= 0))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_workers_list ON workers (plant_id, active, department);

CREATE TABLE attendance (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    plant_id        BIGINT          NOT NULL,
    worker_id       BIGINT          NOT NULL,
    work_date       DATE            NOT NULL,          -- the production date (a night shift belongs to the day it started)
    shift_id        BIGINT,
    status          VARCHAR(10)     NOT NULL,          -- PRESENT / HALF / ABSENT / LEAVE
    ot_hours        DECIMAL(4, 1)   NOT NULL DEFAULT 0,
    in_time         TIME,                              -- from the fingerprint machine, when there is one
    out_time        TIME,
    source          VARCHAR(12)     NOT NULL,          -- CARD (signed sheet) / FINGERPRINT
    note            VARCHAR(255),
    created_at      DATETIME(6)     NOT NULL,
    updated_at      DATETIME(6)     NOT NULL,
    created_by      VARCHAR(100),                      -- who marked it: the supervisor's signature
    updated_by      VARCHAR(100),
    version         BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_attendance_day UNIQUE (worker_id, work_date),
    CONSTRAINT fk_attendance_plant FOREIGN KEY (plant_id) REFERENCES plants (id),
    CONSTRAINT fk_attendance_worker FOREIGN KEY (worker_id) REFERENCES workers (id),
    CONSTRAINT fk_attendance_shift FOREIGN KEY (shift_id) REFERENCES shifts (id),
    CONSTRAINT ck_attendance_ot CHECK (ot_hours BETWEEN 0 AND 16)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_attendance_day ON attendance (plant_id, work_date);

-- ---------------------------------------------------------------------
-- Starter roles (same rules as V9-V19: only roles this project created, never the user's own).
-- ---------------------------------------------------------------------
INSERT INTO roles (name, description, system_role, created_at, updated_at, created_by, updated_by)
SELECT 'Labour office', 'Starter role: keeps the workers and their rates, checks attendance, and makes the month per contractor.',
       FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'Labour office');

INSERT INTO role_permissions (role_id, permission)
SELECT r.id, p.permission
FROM roles r
CROSS JOIN (SELECT 'LABOUR_VIEW' AS permission UNION ALL SELECT 'LABOUR_MANAGE' UNION ALL SELECT 'ATTENDANCE_ENTRY'
            UNION ALL SELECT 'PARTY_VIEW') p   -- PARTY_VIEW: to look up the contractors
WHERE r.name = 'Labour office' AND r.created_by = 'system' AND r.system_role = FALSE
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission = p.permission);

-- Whoever runs a section signs for its workers.
INSERT INTO role_permissions (role_id, permission)
SELECT r.id, 'ATTENDANCE_ENTRY'
FROM roles r
WHERE r.name IN ('Body prep supervisor', 'Press supervisor', 'Glaze supervisor', 'Kiln supervisor', 'Polish supervisor',
                 'Packing supervisor', 'Production manager', 'Maintenance in-charge', 'FG warehouse keeper',
                 'Loading supervisor', 'Store keeper', 'Lab chemist')
  AND r.created_by = 'system' AND r.system_role = FALSE
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission = 'ATTENDANCE_ENTRY');

INSERT INTO role_permissions (role_id, permission)
SELECT r.id, 'LABOUR_VIEW'
FROM roles r
WHERE r.name IN ('Production manager', 'Viewer (read only)')
  AND r.created_by = 'system' AND r.system_role = FALSE
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission = 'LABOUR_VIEW');
