-- =====================================================================
-- V2: Product masters - sizes, finishes, categories, grades, designs.
-- These are the fixed lists that production, stock and sales pick from.
-- Shared by all companies and plants in this installation.
-- =====================================================================

-- Tile size, e.g. 600 x 1200 mm, 9 mm thick, 2 pieces per box.
CREATE TABLE sizes (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    code              VARCHAR(20)   NOT NULL,
    name              VARCHAR(60)   NOT NULL,
    length_mm         INT           NOT NULL,
    width_mm          INT           NOT NULL,
    thickness_mm      DECIMAL(4, 1) NOT NULL,
    pcs_per_box       INT           NOT NULL,
    -- Calculated by the app: length x width x pieces (600 x 1200 x 2 = 1.44 sq m)
    sqm_per_box       DECIMAL(8, 4) NOT NULL,
    box_weight_kg     DECIMAL(6, 2),
    boxes_per_pallet  INT,
    active            BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    created_by        VARCHAR(100),
    updated_by        VARCHAR(100),
    version           BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_sizes_code UNIQUE (code),
    -- CHECK = the database itself refuses impossible values, even if the app has a bug.
    CONSTRAINT ck_sizes_dimensions CHECK (length_mm > 0 AND width_mm > 0 AND thickness_mm > 0),
    CONSTRAINT ck_sizes_pcs CHECK (pcs_per_box > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Surface finish: Glossy, Matt, Satin, Carving ...
CREATE TABLE finishes (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(20)  NOT NULL,
    name        VARCHAR(60)  NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    created_by  VARCHAR(100),
    updated_by  VARCHAR(100),
    version     BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_finishes_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Product type: GVT, PGVT, Full Body ... with the HSN code and GST rate used on invoices.
CREATE TABLE product_categories (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    code        VARCHAR(20)   NOT NULL,
    name        VARCHAR(80)   NOT NULL,
    hsn_code    VARCHAR(8),
    gst_rate    DECIMAL(5, 2) NOT NULL DEFAULT 18.00,
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    created_by  VARCHAR(100),
    updated_by  VARCHAR(100),
    version     BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_product_categories_code UNIQUE (code),
    CONSTRAINT ck_product_categories_gst CHECK (gst_rate >= 0 AND gst_rate <= 100)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Quality grade after sorting. sort_order decides the order in lists (best first).
-- sellable = FALSE for rejects/broken, so they never appear on a sales order.
CREATE TABLE grades (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(20)  NOT NULL,
    name        VARCHAR(60)  NOT NULL,
    sort_order  INT          NOT NULL DEFAULT 0,
    sellable    BOOLEAN      NOT NULL DEFAULT TRUE,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    created_by  VARCHAR(100),
    updated_by  VARCHAR(100),
    version     BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_grades_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- A design = one product you sell. It combines a category, a size and a finish.
-- faces = how many print variations the digital printer cycles through.
CREATE TABLE designs (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    code         VARCHAR(30)   NOT NULL,
    name         VARCHAR(120)  NOT NULL,
    category_id  BIGINT        NOT NULL,
    size_id      BIGINT        NOT NULL,
    finish_id    BIGINT        NOT NULL,
    faces        INT           NOT NULL DEFAULT 1,
    launch_date  DATE,
    description  VARCHAR(255),
    active       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at   DATETIME(6)   NOT NULL,
    updated_at   DATETIME(6)   NOT NULL,
    created_by   VARCHAR(100),
    updated_by   VARCHAR(100),
    version      BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_designs_code UNIQUE (code),
    CONSTRAINT fk_designs_category FOREIGN KEY (category_id) REFERENCES product_categories (id),
    CONSTRAINT fk_designs_size FOREIGN KEY (size_id) REFERENCES sizes (id),
    CONSTRAINT fk_designs_finish FOREIGN KEY (finish_id) REFERENCES finishes (id),
    CONSTRAINT ck_designs_faces CHECK (faces >= 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- Starting data, so the lists are not empty on day one.
-- You can edit or deactivate any of these later from the app.
-- ---------------------------------------------------------------------
INSERT INTO sizes (code, name, length_mm, width_mm, thickness_mm, pcs_per_box, sqm_per_box,
                   active, created_at, updated_at, created_by, updated_by)
VALUES ('600X1200', '600 x 1200 mm', 600, 1200, 9.0, 2, 1.4400,
        TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system');

INSERT INTO finishes (code, name, active, created_at, updated_at, created_by, updated_by)
VALUES ('GLOSSY',  'Glossy',  TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
       ('MATT',    'Matt',    TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
       ('SATIN',   'Satin',   TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
       ('CARVING', 'Carving', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system');

INSERT INTO product_categories (code, name, hsn_code, gst_rate, active, created_at, updated_at, created_by, updated_by)
VALUES ('GVT',  'Glazed Vitrified Tiles',          '6907', 18.00, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
       ('PGVT', 'Polished Glazed Vitrified Tiles', '6907', 18.00, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system');

INSERT INTO grades (code, name, sort_order, sellable, active, created_at, updated_at, created_by, updated_by)
VALUES ('PREMIUM',    'Premium',    1, TRUE,  TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
       ('STANDARD',   'Standard',   2, TRUE,  TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
       ('COMMERCIAL', 'Commercial', 3, TRUE,  TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
       ('REJECT',     'Reject',     9, FALSE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system');