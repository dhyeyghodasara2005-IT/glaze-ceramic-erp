-- =====================================================================
-- V5: Raw material store - godowns, documents (GRN / issue / adjustment),
-- stock balances and the stock ledger.
--
-- How stock works:
--   * A document (GRN, issue, adjustment) is first a DRAFT - it changes nothing.
--   * POSTING it writes one stock_transactions row per line (+ for in, - for out)
--     and updates stock_balances in the same database transaction.
--   * CANCELLING a posted document writes the opposite rows. Nothing is ever deleted,
--     so the ledger is a complete history.
-- =====================================================================

-- Godown / store location inside a plant: Main store, Clay yard, Silo-1 ...
CREATE TABLE store_locations (
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
    CONSTRAINT uk_store_locations_plant_code UNIQUE (plant_id, code),
    CONSTRAINT fk_store_locations_plant FOREIGN KEY (plant_id) REFERENCES plants (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Last number used per plant, document type and financial year (Apr-Mar).
-- Locked while a document is posted so two people never get the same number.
CREATE TABLE document_sequences (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    plant_id     BIGINT       NOT NULL,
    doc_type     VARCHAR(20)  NOT NULL,
    fin_year     VARCHAR(5)   NOT NULL,          -- "26-27"
    last_number  INT          NOT NULL DEFAULT 0,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    created_by   VARCHAR(100),
    updated_by   VARCHAR(100),
    version      BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_document_sequences UNIQUE (plant_id, doc_type, fin_year),
    CONSTRAINT fk_document_sequences_plant FOREIGN KEY (plant_id) REFERENCES plants (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- One header table for all store documents; columns that only apply to one type are NULL for the others.
CREATE TABLE store_documents (
    id                     BIGINT         NOT NULL AUTO_INCREMENT,
    plant_id               BIGINT         NOT NULL,
    doc_type               VARCHAR(20)    NOT NULL,     -- GRN / ISSUE / ADJUSTMENT
    doc_no                 VARCHAR(40),                 -- given when posted: UNIT-1/GRN/26-27/0001
    doc_date               DATE           NOT NULL,
    status                 VARCHAR(20)    NOT NULL,     -- DRAFT / POSTED / CANCELLED

    -- GRN
    supplier_id            BIGINT,
    supplier_invoice_no    VARCHAR(40),
    supplier_invoice_date  DATE,
    vehicle_no             VARCHAR(20),
    weighbridge_slip_no    VARCHAR(30),
    -- ISSUE
    machine_id             BIGINT,
    issued_to              VARCHAR(100),                -- department / person
    -- ADJUSTMENT
    reason                 VARCHAR(20),                 -- OPENING / PHYSICAL_COUNT / DAMAGE / OTHER

    remarks                VARCHAR(500),
    total_value            DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    posted_at              DATETIME(6),
    posted_by              VARCHAR(100),
    cancelled_at           DATETIME(6),
    cancelled_by           VARCHAR(100),
    cancel_reason          VARCHAR(255),
    created_at             DATETIME(6)    NOT NULL,
    updated_at             DATETIME(6)    NOT NULL,
    created_by             VARCHAR(100),
    updated_by             VARCHAR(100),
    version                BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_store_documents_doc_no UNIQUE (doc_no),   -- many NULLs (drafts) allowed
    CONSTRAINT fk_store_documents_plant FOREIGN KEY (plant_id) REFERENCES plants (id),
    CONSTRAINT fk_store_documents_supplier FOREIGN KEY (supplier_id) REFERENCES parties (id),
    CONSTRAINT fk_store_documents_machine FOREIGN KEY (machine_id) REFERENCES machines (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_store_documents_list ON store_documents (plant_id, doc_type, doc_date);

-- Lines of a document. quantity = what moves the stock:
--   GRN: accepted qty (= received - rejected) | ISSUE: issued qty | ADJUSTMENT: +/- qty
CREATE TABLE store_document_lines (
    id             BIGINT          NOT NULL AUTO_INCREMENT,
    document_id    BIGINT          NOT NULL,
    line_no        INT             NOT NULL,
    material_id    BIGINT          NOT NULL,
    location_id    BIGINT          NOT NULL,
    quantity       DECIMAL(16, 3)  NOT NULL,
    received_qty   DECIMAL(16, 3),
    rejected_qty   DECIMAL(16, 3),
    rate           DECIMAL(14, 4),
    value          DECIMAL(18, 2),
    moisture_pct   DECIMAL(5, 2),
    remarks        VARCHAR(255),
    created_at     DATETIME(6)     NOT NULL,
    updated_at     DATETIME(6)     NOT NULL,
    created_by     VARCHAR(100),
    updated_by     VARCHAR(100),
    version        BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_store_lines_document FOREIGN KEY (document_id) REFERENCES store_documents (id) ON DELETE CASCADE,
    CONSTRAINT fk_store_lines_material FOREIGN KEY (material_id) REFERENCES materials (id),
    CONSTRAINT fk_store_lines_location FOREIGN KEY (location_id) REFERENCES store_locations (id),
    CONSTRAINT ck_store_lines_moisture CHECK (moisture_pct IS NULL OR (moisture_pct >= 0 AND moisture_pct <= 100))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Current stock of one material in one godown (a fast summary of the ledger).
-- value / quantity = weighted average rate.
CREATE TABLE stock_balances (
    id           BIGINT          NOT NULL AUTO_INCREMENT,
    plant_id     BIGINT          NOT NULL,
    location_id  BIGINT          NOT NULL,
    material_id  BIGINT          NOT NULL,
    quantity     DECIMAL(16, 3)  NOT NULL DEFAULT 0,
    value        DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    created_at   DATETIME(6)     NOT NULL,
    updated_at   DATETIME(6)     NOT NULL,
    created_by   VARCHAR(100),
    updated_by   VARCHAR(100),
    version      BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_stock_balances UNIQUE (location_id, material_id),
    CONSTRAINT fk_stock_balances_plant FOREIGN KEY (plant_id) REFERENCES plants (id),
    CONSTRAINT fk_stock_balances_location FOREIGN KEY (location_id) REFERENCES store_locations (id),
    CONSTRAINT fk_stock_balances_material FOREIGN KEY (material_id) REFERENCES materials (id),
    CONSTRAINT ck_stock_balances_not_negative CHECK (quantity >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_stock_balances_plant ON stock_balances (plant_id, material_id);

-- The stock ledger: every movement ever, never edited or deleted.
CREATE TABLE stock_transactions (
    id           BIGINT          NOT NULL AUTO_INCREMENT,
    plant_id     BIGINT          NOT NULL,
    location_id  BIGINT          NOT NULL,
    material_id  BIGINT          NOT NULL,
    txn_date     DATE            NOT NULL,
    quantity     DECIMAL(16, 3)  NOT NULL,     -- + in, - out
    rate         DECIMAL(14, 4)  NOT NULL,
    value        DECIMAL(18, 2)  NOT NULL,     -- + in, - out
    balance_qty  DECIMAL(16, 3)  NOT NULL,     -- stock in this godown right after this movement
    document_id  BIGINT          NOT NULL,
    doc_type     VARCHAR(20)     NOT NULL,
    doc_no       VARCHAR(40)     NOT NULL,
    reversal     BOOLEAN         NOT NULL DEFAULT FALSE,   -- TRUE for rows written by a cancellation
    created_at   DATETIME(6)     NOT NULL,
    updated_at   DATETIME(6)     NOT NULL,
    created_by   VARCHAR(100),
    updated_by   VARCHAR(100),
    version      BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_stock_txn_plant FOREIGN KEY (plant_id) REFERENCES plants (id),
    CONSTRAINT fk_stock_txn_location FOREIGN KEY (location_id) REFERENCES store_locations (id),
    CONSTRAINT fk_stock_txn_material FOREIGN KEY (material_id) REFERENCES materials (id),
    CONSTRAINT fk_stock_txn_document FOREIGN KEY (document_id) REFERENCES store_documents (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_stock_txn_material ON stock_transactions (plant_id, material_id, id);
