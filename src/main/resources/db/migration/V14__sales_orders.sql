-- =====================================================================
-- V14: Step 6b - sales orders from loading slips.
--
-- A "loading slip" (DO) comes from the sales system as a PDF for one sales
-- order: which products, how many boxes, for which party. Glaze keeps it as a
-- sales order, matches every line to a design and grade, and (step 6c) plans
-- the loading from it.
--
-- Two kinds of order:
--   SALE     the bill goes to the dealer named on the slip
--   JOBWORK  the slip hides the party; the bill goes to the company whose
--            jobwork it is (the principal) - a new kind of party.
-- =====================================================================

CREATE TABLE sales_orders (
    id               BIGINT          NOT NULL AUTO_INCREMENT,
    plant_id         BIGINT          NOT NULL,
    slip_no          VARCHAR(30)     NOT NULL,         -- Loading Slip No. from the sales system: 460143
    slip_date        DATE            NOT NULL,
    order_ref        VARCHAR(30),                      -- the sales system's Sales Order No.: 3436547
    order_date       DATE,
    order_type       VARCHAR(20)     NOT NULL,         -- SALE / JOBWORK
    party_id         BIGINT          NOT NULL,         -- who is billed: the dealer, or the jobwork principal
    transporter_id   BIGINT,                           -- only when the factory arranges the transport
    status           VARCHAR(20)     NOT NULL,         -- DRAFT / CONFIRMED / CANCELLED (step 6c adds loading)
    source           VARCHAR(20)     NOT NULL,         -- TYPED now; PDF once the slip reader is built
    slip_boxes       INT,                              -- "Total No. of Boxes" printed on the slip
    slip_weight_kg   DECIMAL(10, 1),                   -- "Total Weight" printed on the slip
    remarks          VARCHAR(500),                     -- the slip's narration
    confirmed_at     DATETIME(6),
    confirmed_by     VARCHAR(100),
    cancelled_at     DATETIME(6),
    cancelled_by     VARCHAR(100),
    cancel_reason    VARCHAR(255),
    created_at       DATETIME(6)     NOT NULL,
    updated_at       DATETIME(6)     NOT NULL,
    created_by       VARCHAR(100),
    updated_by       VARCHAR(100),
    version          BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    -- one slip is entered once per plant: a second upload of the same PDF is refused
    CONSTRAINT uk_sales_orders_slip UNIQUE (plant_id, slip_no),
    CONSTRAINT fk_so_plant FOREIGN KEY (plant_id) REFERENCES plants (id),
    CONSTRAINT fk_so_party FOREIGN KEY (party_id) REFERENCES parties (id),
    CONSTRAINT fk_so_transporter FOREIGN KEY (transporter_id) REFERENCES parties (id),
    CONSTRAINT ck_so_slip_totals CHECK ((slip_boxes IS NULL OR slip_boxes > 0) AND (slip_weight_kg IS NULL OR slip_weight_kg > 0))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_sales_orders_list ON sales_orders (plant_id, status, slip_date);

CREATE TABLE sales_order_lines (
    id            BIGINT          NOT NULL AUTO_INCREMENT,
    order_id      BIGINT          NOT NULL,
    line_no       INT             NOT NULL,
    description   VARCHAR(200)    NOT NULL,           -- exactly as printed: O7VM 600X1200-2 FLAVOUR IVORY PDR-P
    design_id     BIGINT,                             -- matched; needed before the order is confirmed
    grade_id      BIGINT,
    boxes         INT             NOT NULL,
    mrp_box       DECIMAL(10, 2),                     -- printed MRP per box (not the bill rate)
    mrp_piece     DECIMAL(10, 2),                     -- printed MRP per piece
    batch_ref     VARCHAR(30),                        -- the slip's Batch No. (production batch), for reference
    created_at    DATETIME(6)     NOT NULL,
    updated_at    DATETIME(6)     NOT NULL,
    created_by    VARCHAR(100),
    updated_by    VARCHAR(100),
    version       BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_so_line_order FOREIGN KEY (order_id) REFERENCES sales_orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_so_line_design FOREIGN KEY (design_id) REFERENCES designs (id),
    CONSTRAINT fk_so_line_grade FOREIGN KEY (grade_id) REFERENCES grades (id),
    CONSTRAINT ck_so_line_boxes CHECK (boxes > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Remembered matches: the sales system's product description -> design + grade.
-- Written when a person confirms an order, so the next slip with the same product needs nobody.
CREATE TABLE product_aliases (
    id            BIGINT          NOT NULL AUTO_INCREMENT,
    name_key      VARCHAR(200)    NOT NULL,           -- the description, tidied: O7VM 600X1200-2 FLAVOUR IVORY PDR-P
    design_id     BIGINT          NOT NULL,
    grade_id      BIGINT          NOT NULL,
    created_at    DATETIME(6)     NOT NULL,
    updated_at    DATETIME(6)     NOT NULL,
    created_by    VARCHAR(100),
    updated_by    VARCHAR(100),
    version       BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_product_aliases UNIQUE (name_key),
    CONSTRAINT fk_alias_design FOREIGN KEY (design_id) REFERENCES designs (id),
    CONSTRAINT fk_alias_grade FOREIGN KEY (grade_id) REFERENCES grades (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- Starter role for the dispatch office (same rules as V9-V11).
-- ---------------------------------------------------------------------
INSERT INTO roles (name, description, system_role, created_at, updated_at, created_by, updated_by)
SELECT 'Dispatch office', 'Starter role: sales orders from loading slips; sees finished-goods stock and parties.', FALSE,
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'Dispatch office');

INSERT INTO role_permissions (role_id, permission)
SELECT r.id, p.permission
FROM roles r
CROSS JOIN (SELECT 'SALES_VIEW' AS permission UNION ALL SELECT 'SALES_ENTRY' UNION ALL SELECT 'FG_VIEW'
            UNION ALL SELECT 'PARTY_VIEW') p
WHERE r.name = 'Dispatch office' AND r.created_by = 'system' AND r.system_role = FALSE
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission = p.permission);

INSERT INTO role_permissions (role_id, permission)
SELECT r.id, 'SALES_VIEW'
FROM roles r
WHERE r.name = 'Viewer (read only)' AND r.created_by = 'system' AND r.system_role = FALSE
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission = 'SALES_VIEW');
