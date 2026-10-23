-- =====================================================================
-- V11: Step 5a - finished-goods stock.
--
--   pack entry (posted) ──▶ fg_stock  (boxes per pillar x batch x grade x shade x caliber)
--                           fg_moves  (every change, with the balance after it)
--
-- A finished-goods godown has pillars (A1, A2, A3 ...). The packing supervisor
-- chooses the pillar for each row of boxes, and the forklift stacks the pallets
-- there (rows of pallets under one pillar, up to 3 or 4 high).
-- =====================================================================

CREATE TABLE fg_godowns (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    plant_id    BIGINT        NOT NULL,
    code        VARCHAR(20)   NOT NULL,
    name        VARCHAR(80)   NOT NULL,
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    created_by  VARCHAR(100),
    updated_by  VARCHAR(100),
    version     BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_fg_godowns_code UNIQUE (plant_id, code),
    CONSTRAINT fk_fg_godown_plant FOREIGN KEY (plant_id) REFERENCES plants (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE fg_pillars (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    godown_id         BIGINT        NOT NULL,
    code              VARCHAR(10)   NOT NULL,          -- A1, A2 ... as painted on the pillar
    capacity_pallets  INT,                             -- how many pallets fit (rows x 3 or 4 high); empty = not set
    active            BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    created_by        VARCHAR(100),
    updated_by        VARCHAR(100),
    version           BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_fg_pillars_code UNIQUE (godown_id, code),
    CONSTRAINT fk_fg_pillar_godown FOREIGN KEY (godown_id) REFERENCES fg_godowns (id),
    CONSTRAINT ck_fg_pillar_capacity CHECK (capacity_pallets IS NULL OR capacity_pallets > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- The packing supervisor assigns a pillar to every row of boxes. The same grade, shade and
-- caliber may now be split over two pillars (when one is full), so the pillar joins the unique key.
-- The new key is added first: it starts with entry_id, so the foreign key always has an index.
ALTER TABLE pack_entry_lines
    ADD COLUMN pillar_id BIGINT AFTER caliber,
    ADD CONSTRAINT fk_pack_line_pillar FOREIGN KEY (pillar_id) REFERENCES fg_pillars (id),
    ADD CONSTRAINT uk_pack_line_pillar UNIQUE (entry_id, grade_id, shade, caliber, pillar_id);
ALTER TABLE pack_entry_lines DROP INDEX uk_pack_line;

-- ---------------------------------------------------------------------
-- Stock: one row per pillar + batch + grade + shade + caliber, in boxes.
-- batch_code is the lot number for boxes the plant packed (lot_id set), or the
-- code printed on older boxes for opening stock (step 5b, lot_id empty).
-- ---------------------------------------------------------------------
CREATE TABLE fg_stock (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    plant_id    BIGINT        NOT NULL,
    pillar_id   BIGINT        NOT NULL,
    design_id   BIGINT        NOT NULL,
    lot_id      BIGINT,
    batch_code  VARCHAR(40)   NOT NULL,
    grade_id    BIGINT        NOT NULL,
    shade       VARCHAR(10)   NOT NULL,
    caliber     VARCHAR(10)   NOT NULL,
    boxes       INT           NOT NULL DEFAULT 0,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    created_by  VARCHAR(100),
    updated_by  VARCHAR(100),
    version     BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_fg_stock UNIQUE (pillar_id, design_id, batch_code, grade_id, shade, caliber),
    CONSTRAINT fk_fg_stock_plant FOREIGN KEY (plant_id) REFERENCES plants (id),
    CONSTRAINT fk_fg_stock_pillar FOREIGN KEY (pillar_id) REFERENCES fg_pillars (id),
    CONSTRAINT fk_fg_stock_design FOREIGN KEY (design_id) REFERENCES designs (id),
    CONSTRAINT fk_fg_stock_lot FOREIGN KEY (lot_id) REFERENCES production_lots (id),
    CONSTRAINT fk_fg_stock_grade FOREIGN KEY (grade_id) REFERENCES grades (id),
    -- the database itself refuses to let stock go below zero
    CONSTRAINT ck_fg_stock_boxes CHECK (boxes >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_fg_stock_design ON fg_stock (plant_id, design_id);

-- The ledger: written once, never changed. A cancelled document writes reversal rows.
CREATE TABLE fg_moves (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    plant_id       BIGINT        NOT NULL,
    stock_id       BIGINT        NOT NULL,
    move_date      DATE          NOT NULL,
    boxes          INT           NOT NULL,          -- + in, - out
    balance_boxes  INT           NOT NULL,          -- boxes left in this stock row right after
    source         VARCHAR(20)   NOT NULL,          -- PACK now; OPENING / BREAKAGE / REGRADE / DISPATCH later
    source_id      BIGINT        NOT NULL,
    source_no      VARCHAR(40)   NOT NULL,
    reversal       BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    created_by     VARCHAR(100),
    updated_by     VARCHAR(100),
    version        BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_fg_move_plant FOREIGN KEY (plant_id) REFERENCES plants (id),
    CONSTRAINT fk_fg_move_stock FOREIGN KEY (stock_id) REFERENCES fg_stock (id),
    CONSTRAINT ck_fg_move_boxes CHECK (boxes <> 0 AND balance_boxes >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_fg_move_stock ON fg_moves (stock_id, id);
CREATE INDEX ix_fg_move_source ON fg_moves (source, source_id);

-- ---------------------------------------------------------------------
-- Starter role for the warehouse, and the new view permission for the
-- starter roles that should see finished goods (same rules as V9 / V10).
-- ---------------------------------------------------------------------
INSERT INTO roles (name, description, system_role, created_at, updated_at, created_by, updated_by)
SELECT 'FG warehouse keeper', 'Starter role: finished-goods godowns and pillars, stock.', FALSE,
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'FG warehouse keeper');

INSERT INTO role_permissions (role_id, permission)
SELECT r.id, p.permission
FROM roles r
CROSS JOIN (SELECT 'FG_VIEW' AS permission UNION ALL SELECT 'FG_ENTRY') p
WHERE r.name = 'FG warehouse keeper' AND r.created_by = 'system' AND r.system_role = FALSE
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission = p.permission);

-- The packing supervisor picks pillars "by colour and stock", so they see the stock too.
INSERT INTO role_permissions (role_id, permission)
SELECT r.id, 'FG_VIEW'
FROM roles r
WHERE r.name IN ('Production manager', 'Viewer (read only)', 'Packing supervisor')
  AND r.created_by = 'system' AND r.system_role = FALSE
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission = 'FG_VIEW');
