-- =====================================================================
-- V12: Step 5b - the warehouse's own documents for finished goods.
--
--   OPENING   boxes already in the godown on go-live day   (+ in)
--   BREAKAGE  boxes damaged in the godown, written off      (- out)
--   REGRADE   boxes re-checked: new grade / shade /         (- out of the old row,
--             caliber, or moved to another pillar             + into the new one)
--
-- One table for the three, like store_documents (GRN / issue / adjustment).
-- Life cycle as everywhere: DRAFT -> POSTED (number, stock moves) -> CANCELLED (reversed).
-- =====================================================================

CREATE TABLE fg_documents (
    id             BIGINT          NOT NULL AUTO_INCREMENT,
    plant_id       BIGINT          NOT NULL,
    doc_type       VARCHAR(20)     NOT NULL,          -- OPENING / BREAKAGE / REGRADE
    doc_no         VARCHAR(40),                       -- UNIT-1/OS/26-27/0001, given when posted
    doc_date       DATE            NOT NULL,
    status         VARCHAR(20)     NOT NULL,          -- DRAFT / POSTED / CANCELLED
    remarks        VARCHAR(500),
    posted_at      DATETIME(6),
    posted_by      VARCHAR(100),
    cancelled_at   DATETIME(6),
    cancelled_by   VARCHAR(100),
    cancel_reason  VARCHAR(255),
    created_at     DATETIME(6)     NOT NULL,
    updated_at     DATETIME(6)     NOT NULL,
    created_by     VARCHAR(100),
    updated_by     VARCHAR(100),
    version        BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_fg_documents_no UNIQUE (doc_no),
    CONSTRAINT fk_fg_doc_plant FOREIGN KEY (plant_id) REFERENCES plants (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_fg_documents_list ON fg_documents (plant_id, doc_type, doc_date);

-- Which columns a line uses depends on the document type:
--   OPENING   pillar, design, batch_code, grade, shade, caliber, boxes
--   BREAKAGE  from_stock, boxes, reason
--   REGRADE   from_stock, boxes, and the new grade, shade, caliber and pillar
CREATE TABLE fg_document_lines (
    id             BIGINT          NOT NULL AUTO_INCREMENT,
    document_id    BIGINT          NOT NULL,
    line_no        INT             NOT NULL,
    from_stock_id  BIGINT,                            -- the boxes taken (breakage, regrade)
    pillar_id      BIGINT,                            -- where the boxes go (opening, regrade)
    design_id      BIGINT,                            -- opening
    batch_code     VARCHAR(40),                       -- opening: the batch printed on the boxes
    grade_id       BIGINT,                            -- opening, regrade: the (new) grade
    shade          VARCHAR(10),
    caliber        VARCHAR(10),
    boxes          INT             NOT NULL,
    reason         VARCHAR(20),                       -- breakage: HANDLING / LOADING / STORAGE / OTHER
    remarks        VARCHAR(255),
    created_at     DATETIME(6)     NOT NULL,
    updated_at     DATETIME(6)     NOT NULL,
    created_by     VARCHAR(100),
    updated_by     VARCHAR(100),
    version        BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_fg_line_document FOREIGN KEY (document_id) REFERENCES fg_documents (id) ON DELETE CASCADE,
    CONSTRAINT fk_fg_line_stock FOREIGN KEY (from_stock_id) REFERENCES fg_stock (id),
    CONSTRAINT fk_fg_line_pillar FOREIGN KEY (pillar_id) REFERENCES fg_pillars (id),
    CONSTRAINT fk_fg_line_design FOREIGN KEY (design_id) REFERENCES designs (id),
    CONSTRAINT fk_fg_line_grade FOREIGN KEY (grade_id) REFERENCES grades (id),
    CONSTRAINT ck_fg_line_boxes CHECK (boxes > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
