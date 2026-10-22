-- =====================================================================
-- V8: Production step 4c - glaze & digital print line, and the kiln.
--
--   PRESS -> DRYER --> GLAZE LINE + DIGITAL PRINTER --> KILN --> sorting (step 4d)
--                      (stage GLAZE)                    (stage KILN)
--
-- Both stages record the same thing per lot and shift - tiles in, tiles
-- rejected - plus their own readings, so they share ONE table with a
-- "stage" column (the same idea as store_documents for GRN / issue / adjustment).
-- =====================================================================

CREATE TABLE line_entries (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    plant_id        BIGINT          NOT NULL,
    entry_no        VARCHAR(40),                      -- UNIT-1/GL/26-27/0001 or UNIT-1/KL/26-27/0001, given when posted
    stage           VARCHAR(20)     NOT NULL,         -- GLAZE / KILN
    lot_id          BIGINT          NOT NULL,
    prod_date       DATE            NOT NULL,
    shift_id        BIGINT          NOT NULL,
    machine_id      BIGINT          NOT NULL,         -- the glaze line, or the kiln
    printer_id      BIGINT,                           -- digital printer (glaze stage only)
    status          VARCHAR(20)     NOT NULL,         -- DRAFT / POSTED / CANCELLED
    tiles_in        INT             NOT NULL,
    rejects         INT             NOT NULL DEFAULT 0,
    -- glaze & print readings
    engobe_gpt      DECIMAL(7, 1),                    -- engobe applied, grams per tile
    glaze_gpt       DECIMAL(7, 1),                    -- glaze applied, grams per tile
    glaze_density   DECIMAL(6, 1),                    -- glaze litre weight, g/L
    ink_litres      DECIMAL(8, 2),                    -- digital ink used in the shift
    -- kiln readings
    max_temp        DECIMAL(6, 1),                    -- peak firing temperature, deg C
    cycle_min       DECIMAL(5, 1),                    -- firing cycle, minutes
    gas_scm         DECIMAL(12, 2),                   -- kiln gas used in the shift
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
    CONSTRAINT uk_line_entries_no UNIQUE (entry_no),
    CONSTRAINT fk_line_plant FOREIGN KEY (plant_id) REFERENCES plants (id),
    CONSTRAINT fk_line_lot FOREIGN KEY (lot_id) REFERENCES production_lots (id),
    CONSTRAINT fk_line_shift FOREIGN KEY (shift_id) REFERENCES shifts (id),
    CONSTRAINT fk_line_machine FOREIGN KEY (machine_id) REFERENCES machines (id),
    CONSTRAINT fk_line_printer FOREIGN KEY (printer_id) REFERENCES machines (id),
    CONSTRAINT ck_line_counts CHECK (tiles_in > 0 AND rejects >= 0 AND rejects <= tiles_in)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_line_list ON line_entries (plant_id, stage, prod_date);
CREATE INDEX ix_line_lot ON line_entries (lot_id, stage, status);
