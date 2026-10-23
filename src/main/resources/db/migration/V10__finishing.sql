-- =====================================================================
-- V10: Production step 4d - from the kiln to the box.
--
--   KILN --> cooling area ------> POLISHING LINE --> dark-room sorting + packing
--            quality check:       (stage POLISH,     (pack entry: boxes by
--            bend, size,           line_entries)      grade x shade x caliber,
--            thickness                                plus broken pieces)
--
-- The quality check moves no tiles: it tells everyone early how the lot
-- will sort. Polishing is one more stage of line_entries. The pack entry
-- is what step 5 will turn into finished-goods stock.
-- =====================================================================

-- Polishing records one reading of its own: the power the line used in the shift.
ALTER TABLE line_entries ADD COLUMN power_kwh DECIMAL(12, 2) AFTER gas_scm;

-- ---------------------------------------------------------------------
-- Quality check in the cooling area, against a lot. Final when saved
-- (it gets its number straight away); a wrong one is cancelled, not edited.
-- ---------------------------------------------------------------------
CREATE TABLE lot_checks (
    id                BIGINT          NOT NULL AUTO_INCREMENT,
    plant_id          BIGINT          NOT NULL,
    check_no          VARCHAR(40)     NOT NULL,         -- UNIT-1/QC/26-27/0001
    lot_id            BIGINT          NOT NULL,
    check_date        DATE            NOT NULL,
    shift_id          BIGINT          NOT NULL,
    status            VARCHAR(20)     NOT NULL,         -- POSTED / CANCELLED
    samples           INT             NOT NULL,         -- tiles checked
    centre_bend_mm    DECIMAL(5, 2),                    -- + = bulges up (convex), - = dips (concave)
    edge_bend_mm      DECIMAL(5, 2),
    warpage_mm        DECIMAL(5, 2),                    -- one corner lifted off the flat
    length_mm         DECIMAL(7, 2),
    width_mm          DECIMAL(7, 2),
    thickness_mm      DECIMAL(5, 2),
    verdict           VARCHAR(20)     NOT NULL,         -- GOOD / WATCH / PROBLEM
    expected_top_pct  DECIMAL(5, 2),                    -- share expected in the best grade, %
    remarks           VARCHAR(500),
    cancelled_at      DATETIME(6),
    cancelled_by      VARCHAR(100),
    cancel_reason     VARCHAR(255),
    created_at        DATETIME(6)     NOT NULL,
    updated_at        DATETIME(6)     NOT NULL,
    created_by        VARCHAR(100),
    updated_by        VARCHAR(100),
    version           BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_lot_checks_no UNIQUE (check_no),
    CONSTRAINT fk_check_plant FOREIGN KEY (plant_id) REFERENCES plants (id),
    CONSTRAINT fk_check_lot FOREIGN KEY (lot_id) REFERENCES production_lots (id),
    CONSTRAINT fk_check_shift FOREIGN KEY (shift_id) REFERENCES shifts (id),
    CONSTRAINT ck_check_samples CHECK (samples > 0),
    CONSTRAINT ck_check_pct CHECK (expected_top_pct IS NULL OR (expected_top_pct >= 0 AND expected_top_pct <= 100))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_check_list ON lot_checks (plant_id, check_date);
CREATE INDEX ix_check_lot ON lot_checks (lot_id, status);

-- ---------------------------------------------------------------------
-- Sorting & packing: one shift of one lot, entered by the packing supervisor.
-- The totals are worked out by the app from the lines and stored, so the
-- funnel can add up whole entries without opening every line.
-- ---------------------------------------------------------------------
CREATE TABLE pack_entries (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    plant_id        BIGINT          NOT NULL,
    entry_no        VARCHAR(40),                      -- UNIT-1/PK/26-27/0001, given when posted
    lot_id          BIGINT          NOT NULL,
    prod_date       DATE            NOT NULL,
    shift_id        BIGINT          NOT NULL,
    machine_id      BIGINT          NOT NULL,         -- the sorting line
    status          VARCHAR(20)     NOT NULL,         -- DRAFT / POSTED / CANCELLED
    tiles_sorted    INT             NOT NULL,         -- pieces packed + broken
    pieces_packed   INT             NOT NULL,
    boxes           INT             NOT NULL,
    broken          INT             NOT NULL DEFAULT 0,
    remarks         VARCHAR(500),
    posted_at       DATETIME(6),
    posted_by       VARCHAR(100),
    cancelled_at    DATETIME(6),
    cancelled_by    VARCHAR(100),
    cancel_reason   VARCHAR(255),
    created_at      DATETIME(6)     NOT NULL,
    updated_at      DATETIME(6)     NOT NULL,
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    version         BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_pack_entries_no UNIQUE (entry_no),
    CONSTRAINT fk_pack_plant FOREIGN KEY (plant_id) REFERENCES plants (id),
    CONSTRAINT fk_pack_lot FOREIGN KEY (lot_id) REFERENCES production_lots (id),
    CONSTRAINT fk_pack_shift FOREIGN KEY (shift_id) REFERENCES shifts (id),
    CONSTRAINT fk_pack_machine FOREIGN KEY (machine_id) REFERENCES machines (id),
    CONSTRAINT ck_pack_counts CHECK (broken >= 0 AND boxes >= 0 AND pieces_packed >= 0
                                     AND tiles_sorted = pieces_packed + broken)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_pack_list ON pack_entries (plant_id, prod_date);
CREATE INDEX ix_pack_lot ON pack_entries (lot_id, status);

-- One row per grade + shade + caliber: what is printed on the box stickers.
CREATE TABLE pack_entry_lines (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    entry_id     BIGINT        NOT NULL,
    line_no      INT           NOT NULL,
    grade_id     BIGINT        NOT NULL,
    shade        VARCHAR(10)   NOT NULL,
    caliber      VARCHAR(10)   NOT NULL,
    boxes        INT           NOT NULL,
    pieces       INT           NOT NULL,              -- boxes x pieces per box, kept as it was that day
    created_at   DATETIME(6)   NOT NULL,
    updated_at   DATETIME(6)   NOT NULL,
    created_by   VARCHAR(100),
    updated_by   VARCHAR(100),
    version      BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_pack_line UNIQUE (entry_id, grade_id, shade, caliber),
    CONSTRAINT fk_pack_line_entry FOREIGN KEY (entry_id) REFERENCES pack_entries (id) ON DELETE CASCADE,
    CONSTRAINT fk_pack_line_grade FOREIGN KEY (grade_id) REFERENCES grades (id),
    CONSTRAINT ck_pack_line_counts CHECK (boxes > 0 AND pieces > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- The plant grades poor tiles as Commercial or Eco. Add Eco if it isn't there.
-- ---------------------------------------------------------------------
INSERT INTO grades (code, name, sort_order, sellable, active, created_at, updated_at, created_by, updated_by)
SELECT 'ECO', 'Eco', 4, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM grades WHERE code = 'ECO');

-- ---------------------------------------------------------------------
-- Starter roles for the new jobs (same rules as V9: never overwrite a role
-- that already exists, only fill roles this script created).
-- ---------------------------------------------------------------------
INSERT INTO roles (name, description, system_role, created_at, updated_at, created_by, updated_by)
SELECT s.name, s.description, FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'
FROM (
    SELECT 'Polish supervisor' AS name,
           'Starter role: polishing line shift entries.' AS description
    UNION ALL SELECT 'Packing supervisor',
           'Starter role: sorting & packing shift entries - boxes by grade, shade and caliber.'
    UNION ALL SELECT 'Quality checker',
           'Starter role: bend, size and thickness checks in the cooling area.'
) s
WHERE NOT EXISTS (SELECT 1 FROM roles r WHERE r.name = s.name);

INSERT INTO role_permissions (role_id, permission)
SELECT r.id, p.permission
FROM roles r
JOIN (
    SELECT 'Polish supervisor' AS role_name, 'PRODUCTION_VIEW' AS permission
    UNION ALL SELECT 'Polish supervisor', 'POLISH_ENTRY'

    UNION ALL SELECT 'Packing supervisor', 'PRODUCTION_VIEW'
    UNION ALL SELECT 'Packing supervisor', 'PACKING_ENTRY'

    UNION ALL SELECT 'Quality checker', 'PRODUCTION_VIEW'
    UNION ALL SELECT 'Quality checker', 'QUALITY_CHECK'
) p ON p.role_name = r.name
WHERE r.created_by = 'system'
  AND r.system_role = FALSE
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id);

-- The starter "Production manager" covers every division, so it gets the new ones too.
INSERT INTO role_permissions (role_id, permission)
SELECT r.id, p.permission
FROM roles r
CROSS JOIN (
    SELECT 'POLISH_ENTRY' AS permission
    UNION ALL SELECT 'PACKING_ENTRY'
    UNION ALL SELECT 'QUALITY_CHECK'
) p
WHERE r.name = 'Production manager'
  AND r.created_by = 'system'
  AND r.system_role = FALSE
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission = p.permission);
