-- =====================================================================
-- V6: Production step 4a - shifts, body recipes and body preparation
-- (ball mill charges = body batches, spray dryer runs).
--
-- The flow on the shop floor:
--   raw material (store) --charge--> BALL MILL --discharge--> SLIP TANK
--        --spray dryer run--> powder in a SILO --> press (step 4b)
--
-- Charging a mill makes a store ISSUE automatically, so the clay leaves
-- the store stock without anyone entering it twice.
-- =====================================================================

-- Store documents made by another module (e.g. the issue behind a body batch).
-- NULL = typed in the store. Such documents can only be changed from where they came from.
ALTER TABLE store_documents ADD COLUMN origin VARCHAR(20) NULL AFTER status;

-- Working shifts. A shift may cross midnight (Night 20:00 - 08:00): its work belongs
-- to the production date on which it STARTED.
CREATE TABLE shifts (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(10)  NOT NULL,
    name        VARCHAR(40)  NOT NULL,
    start_time  TIME         NOT NULL,
    end_time    TIME         NOT NULL,
    sort_order  INT          NOT NULL DEFAULT 0,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    created_by  VARCHAR(100),
    updated_by  VARCHAR(100),
    version     BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_shifts_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Two 12-hour shifts, as most Morbi plants run. Editable on the Shifts screen.
INSERT INTO shifts (code, name, start_time, end_time, sort_order, active, created_at, updated_at, created_by, updated_by)
VALUES ('A', 'Day',   '08:00:00', '20:00:00', 1, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
       ('B', 'Night', '20:00:00', '08:00:00', 2, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system');

-- Body recipe (body composition): what goes into one tonne of dry ball-mill charge.
CREATE TABLE body_recipes (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    code         VARCHAR(20)   NOT NULL,
    name         VARCHAR(80)   NOT NULL,
    description  VARCHAR(255),
    active       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at   DATETIME(6)   NOT NULL,
    updated_at   DATETIME(6)   NOT NULL,
    created_by   VARCHAR(100),
    updated_by   VARCHAR(100),
    version      BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_body_recipes_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- qty_per_ton is in the material's own unit: clay 0.300 TON (= 30 %), deflocculant 3 KG.
CREATE TABLE body_recipe_lines (
    id           BIGINT          NOT NULL AUTO_INCREMENT,
    recipe_id    BIGINT          NOT NULL,
    line_no      INT             NOT NULL,
    material_id  BIGINT          NOT NULL,
    qty_per_ton  DECIMAL(12, 4)  NOT NULL,
    created_at   DATETIME(6)     NOT NULL,
    updated_at   DATETIME(6)     NOT NULL,
    created_by   VARCHAR(100),
    updated_by   VARCHAR(100),
    version      BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_body_recipe_lines UNIQUE (recipe_id, material_id),
    CONSTRAINT fk_recipe_lines_recipe FOREIGN KEY (recipe_id) REFERENCES body_recipes (id) ON DELETE CASCADE,
    CONSTRAINT fk_recipe_lines_material FOREIGN KEY (material_id) REFERENCES materials (id),
    CONSTRAINT ck_recipe_lines_qty CHECK (qty_per_ton > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- One ball mill charge = one BODY BATCH.
-- Life: DRAFT -> CHARGED (raw material issued) -> DISCHARGED (slip tested, in a tank)
--       -> CONSUMED (used up by the spray dryer).  CANCELLED reverses the store issue.
CREATE TABLE body_batches (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    plant_id            BIGINT          NOT NULL,
    batch_no            VARCHAR(40),                  -- given when charged: UNIT-1/BB/26-27/0001
    prod_date           DATE            NOT NULL,     -- production date (the date the shift started)
    shift_id            BIGINT          NOT NULL,
    ball_mill_id        BIGINT          NOT NULL,
    recipe_id           BIGINT,
    status              VARCHAR(20)     NOT NULL,
    charge_ton          DECIMAL(10, 3)  NOT NULL,     -- dry charge loaded into the mill
    water_litres        DECIMAL(12, 1),
    milling_start       DATETIME(6)     NOT NULL,
    milling_end         DATETIME(6),
    -- slip test at discharge
    slip_tank_id        BIGINT,
    slip_density        DECIMAL(6, 1),                -- litre weight, g/L (e.g. 1700)
    slip_viscosity      DECIMAL(6, 1),                -- flow time, seconds
    slip_residue        DECIMAL(5, 2),                -- % left on the sieve
    issue_document_id   BIGINT,                       -- the store issue made when charged
    remarks             VARCHAR(500),
    charged_at          DATETIME(6),
    charged_by          VARCHAR(100),
    discharged_at       DATETIME(6),
    discharged_by       VARCHAR(100),
    cancelled_at        DATETIME(6),
    cancelled_by        VARCHAR(100),
    cancel_reason       VARCHAR(255),
    created_at          DATETIME(6)     NOT NULL,
    updated_at          DATETIME(6)     NOT NULL,
    created_by          VARCHAR(100),
    updated_by          VARCHAR(100),
    version             BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_body_batches_no UNIQUE (batch_no),
    CONSTRAINT fk_body_batches_plant FOREIGN KEY (plant_id) REFERENCES plants (id),
    CONSTRAINT fk_body_batches_shift FOREIGN KEY (shift_id) REFERENCES shifts (id),
    CONSTRAINT fk_body_batches_mill FOREIGN KEY (ball_mill_id) REFERENCES machines (id),
    CONSTRAINT fk_body_batches_recipe FOREIGN KEY (recipe_id) REFERENCES body_recipes (id),
    CONSTRAINT fk_body_batches_tank FOREIGN KEY (slip_tank_id) REFERENCES machines (id),
    CONSTRAINT fk_body_batches_issue FOREIGN KEY (issue_document_id) REFERENCES store_documents (id),
    CONSTRAINT ck_body_batches_charge CHECK (charge_ton > 0),
    CONSTRAINT ck_body_batches_residue CHECK (slip_residue IS NULL OR (slip_residue >= 0 AND slip_residue <= 100))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_body_batches_list ON body_batches (plant_id, prod_date);
CREATE INDEX ix_body_batches_status ON body_batches (plant_id, status);

-- Raw materials actually loaded, and the godown each came from.
CREATE TABLE body_batch_lines (
    id           BIGINT          NOT NULL AUTO_INCREMENT,
    batch_id     BIGINT          NOT NULL,
    line_no      INT             NOT NULL,
    material_id  BIGINT          NOT NULL,
    location_id  BIGINT          NOT NULL,
    quantity     DECIMAL(16, 3)  NOT NULL,
    created_at   DATETIME(6)     NOT NULL,
    updated_at   DATETIME(6)     NOT NULL,
    created_by   VARCHAR(100),
    updated_by   VARCHAR(100),
    version      BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_batch_lines_batch FOREIGN KEY (batch_id) REFERENCES body_batches (id) ON DELETE CASCADE,
    CONSTRAINT fk_batch_lines_material FOREIGN KEY (material_id) REFERENCES materials (id),
    CONSTRAINT fk_batch_lines_location FOREIGN KEY (location_id) REFERENCES store_locations (id),
    CONSTRAINT ck_batch_lines_qty CHECK (quantity > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- A spray dryer run: slip from body batches -> powder into a silo.
CREATE TABLE spray_runs (
    id                BIGINT          NOT NULL AUTO_INCREMENT,
    plant_id          BIGINT          NOT NULL,
    run_no            VARCHAR(40),                  -- given when posted: UNIT-1/SD/26-27/0001
    prod_date         DATE            NOT NULL,
    shift_id          BIGINT          NOT NULL,
    spray_dryer_id    BIGINT          NOT NULL,
    silo_id           BIGINT          NOT NULL,
    status            VARCHAR(20)     NOT NULL,     -- DRAFT / POSTED / CANCELLED
    run_start         DATETIME(6)     NOT NULL,
    run_end           DATETIME(6),
    powder_ton        DECIMAL(10, 3)  NOT NULL,
    powder_moisture   DECIMAL(5, 2),                -- % (usually 5.5 - 7)
    gas_scm           DECIMAL(12, 2),
    inlet_temp        DECIMAL(6, 1),                -- deg C
    outlet_temp       DECIMAL(6, 1),
    remarks           VARCHAR(500),
    posted_at         DATETIME(6),
    posted_by         VARCHAR(100),
    cancelled_at      DATETIME(6),
    cancelled_by      VARCHAR(100),
    cancel_reason     VARCHAR(255),
    created_at        DATETIME(6)     NOT NULL,
    updated_at        DATETIME(6)     NOT NULL,
    created_by        VARCHAR(100),
    updated_by        VARCHAR(100),
    version           BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_spray_runs_no UNIQUE (run_no),
    CONSTRAINT fk_spray_runs_plant FOREIGN KEY (plant_id) REFERENCES plants (id),
    CONSTRAINT fk_spray_runs_shift FOREIGN KEY (shift_id) REFERENCES shifts (id),
    CONSTRAINT fk_spray_runs_dryer FOREIGN KEY (spray_dryer_id) REFERENCES machines (id),
    CONSTRAINT fk_spray_runs_silo FOREIGN KEY (silo_id) REFERENCES machines (id),
    CONSTRAINT ck_spray_runs_powder CHECK (powder_ton > 0),
    CONSTRAINT ck_spray_runs_moisture CHECK (powder_moisture IS NULL OR (powder_moisture >= 0 AND powder_moisture <= 100))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_spray_runs_list ON spray_runs (plant_id, prod_date);

-- Which body batches a run used. finished = the batch's slip was used up in this run.
-- This table is the trace: powder in a silo -> runs -> body batches -> raw material lots.
CREATE TABLE spray_run_batches (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    run_id         BIGINT       NOT NULL,
    body_batch_id  BIGINT       NOT NULL,
    finished       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    created_by     VARCHAR(100),
    updated_by     VARCHAR(100),
    version        BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_spray_run_batches UNIQUE (run_id, body_batch_id),
    CONSTRAINT fk_run_batches_run FOREIGN KEY (run_id) REFERENCES spray_runs (id) ON DELETE CASCADE,
    CONSTRAINT fk_run_batches_batch FOREIGN KEY (body_batch_id) REFERENCES body_batches (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
