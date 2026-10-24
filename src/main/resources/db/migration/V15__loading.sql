-- =====================================================================
-- V15: Step 6c - the loading plan, the loading checklist and dispatch.
--
-- When a sales order is confirmed, Glaze picks the boxes for every line
-- (which stock row, at which pillar, how many) and keeps them for that
-- order: other orders plan around them. The loader ticks each pick on the
-- phone, notes the truck, and confirms the loading: the boxes leave the
-- stock (a DISPATCH move) and the order becomes DISPATCHED.
-- =====================================================================

-- Each principal's own system numbers its slips, so two principals can print the same slip no.
-- The same slip of the same party is still refused twice. (New key first: the old one also served plant_id.)
ALTER TABLE sales_orders ADD CONSTRAINT uk_sales_orders_party_slip UNIQUE (plant_id, party_id, slip_no);
ALTER TABLE sales_orders DROP INDEX uk_sales_orders_slip;

ALTER TABLE sales_orders
    ADD COLUMN truck_no       VARCHAR(20)  AFTER transporter_id,     -- GJ-36-T-4521
    ADD COLUMN driver_name    VARCHAR(100) AFTER truck_no,
    ADD COLUMN driver_mobile  VARCHAR(15)  AFTER driver_name,
    ADD COLUMN lr_no          VARCHAR(30)  AFTER driver_mobile,      -- transporter's lorry receipt
    ADD COLUMN planned_at     DATETIME(6)  AFTER confirmed_by,       -- when the boxes were last picked
    ADD COLUMN dispatch_no    VARCHAR(40)  AFTER planned_at,         -- gate pass no.: UNIT-1/DP/26-27/0001
    ADD COLUMN dispatch_date  DATE         AFTER dispatch_no,
    ADD COLUMN dispatched_at  DATETIME(6)  AFTER dispatch_date,
    ADD COLUMN dispatched_by  VARCHAR(100) AFTER dispatched_at,
    ADD COLUMN undone_at      DATETIME(6)  AFTER dispatched_by,      -- the last "undo dispatch", if any
    ADD COLUMN undone_by      VARCHAR(100) AFTER undone_at,
    ADD COLUMN undo_reason    VARCHAR(255) AFTER undone_by;

-- The pick list: which boxes each order line takes. While the order is CONFIRMED these boxes are
-- kept for it (planned, or what the loader actually loaded once ticked).
CREATE TABLE sales_order_picks (
    id             BIGINT          NOT NULL AUTO_INCREMENT,
    order_id       BIGINT          NOT NULL,
    line_id        BIGINT          NOT NULL,
    stock_id       BIGINT          NOT NULL,           -- the fg_stock row: pillar x batch x grade x shade x caliber
    planned_boxes  INT             NOT NULL,
    loaded_boxes   INT,                                -- empty until the loader ticks it
    reason         VARCHAR(20)     NOT NULL,           -- why Glaze chose it: CLEARS_PILLAR, OLDEST, ... or ADDED at loading
    loaded_at      DATETIME(6),
    loaded_by      VARCHAR(100),
    created_at     DATETIME(6)     NOT NULL,
    updated_at     DATETIME(6)     NOT NULL,
    created_by     VARCHAR(100),
    updated_by     VARCHAR(100),
    version        BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_so_pick UNIQUE (line_id, stock_id),
    CONSTRAINT fk_so_pick_order FOREIGN KEY (order_id) REFERENCES sales_orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_so_pick_line FOREIGN KEY (line_id) REFERENCES sales_order_lines (id) ON DELETE CASCADE,
    CONSTRAINT fk_so_pick_stock FOREIGN KEY (stock_id) REFERENCES fg_stock (id),
    CONSTRAINT ck_so_pick_boxes CHECK (planned_boxes >= 0 AND (loaded_boxes IS NULL OR loaded_boxes >= 0))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_so_pick_stock ON sales_order_picks (stock_id);

-- One row per plant, locked while boxes are being kept for an order, so two orders planned at the
-- same moment never keep the same boxes. No foreign key: nothing else ever waits on these rows.
CREATE TABLE sales_plan_locks (
    plant_id  BIGINT  NOT NULL,
    PRIMARY KEY (plant_id)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- The loading supervisor (same rules as V9-V14: never touch a role the user made).
-- The dispatch office may confirm loadings too.
-- ---------------------------------------------------------------------
INSERT INTO roles (name, description, system_role, created_at, updated_at, created_by, updated_by)
SELECT 'Loading supervisor', 'Starter role: ticks the boxes as they are loaded, notes the truck and confirms the loading.', FALSE,
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'Loading supervisor');

INSERT INTO role_permissions (role_id, permission)
SELECT r.id, p.permission
FROM roles r
CROSS JOIN (SELECT 'SALES_VIEW' AS permission UNION ALL SELECT 'FG_VIEW' UNION ALL SELECT 'LOADING_ENTRY') p
WHERE r.name = 'Loading supervisor' AND r.created_by = 'system' AND r.system_role = FALSE
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission = p.permission);

INSERT INTO role_permissions (role_id, permission)
SELECT r.id, 'LOADING_ENTRY'
FROM roles r
WHERE r.name = 'Dispatch office' AND r.created_by = 'system' AND r.system_role = FALSE
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission = 'LOADING_ENTRY');
