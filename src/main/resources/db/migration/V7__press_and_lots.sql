-- =====================================================================
-- V7: Production step 4b - design lots, press & dryer entries, silo tracking.
--
--   SILO (powder from spray runs) --press entry draws tonnes, oldest first-->
--   PRESS --> DRYER --> glaze line (step 4c)
--
-- A DESIGN LOT is one run of one design on a press line. Every later stage
-- (glaze, kiln, sorting, packing) will record its numbers against the lot.
-- =====================================================================

-- Powder from a run still in its silo. Spray runs fill it; press entries draw from it, oldest run first.
ALTER TABLE spray_runs ADD COLUMN powder_left DECIMAL(10, 3) NOT NULL DEFAULT 0 AFTER powder_ton;
UPDATE spray_runs SET powder_left = powder_ton WHERE status = 'POSTED';
CREATE INDEX ix_spray_runs_silo ON spray_runs (silo_id, status);

-- One run of one design on one press line: OPEN while the line runs it, then CLOSED.
CREATE TABLE production_lots (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    plant_id         BIGINT        NOT NULL,
    lot_no           VARCHAR(40)   NOT NULL,          -- UNIT-1/LOT/26-27/0001, given when opened
    design_id        BIGINT        NOT NULL,
    press_id         BIGINT        NOT NULL,          -- the line is known by its press
    status           VARCHAR(20)   NOT NULL,          -- OPEN / CLOSED / CANCELLED
    opened_date      DATE          NOT NULL,          -- production date it started
    opened_shift_id  BIGINT        NOT NULL,
    remarks          VARCHAR(500),
    opened_at        DATETIME(6)   NOT NULL,
    opened_by        VARCHAR(100),
    closed_at        DATETIME(6),
    closed_by        VARCHAR(100),
    cancelled_at     DATETIME(6),
    cancelled_by     VARCHAR(100),
    cancel_reason    VARCHAR(255),
    created_at       DATETIME(6)   NOT NULL,
    updated_at       DATETIME(6)   NOT NULL,
    created_by       VARCHAR(100),
    updated_by       VARCHAR(100),
    version          BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_production_lots_no UNIQUE (lot_no),
    CONSTRAINT fk_lots_plant FOREIGN KEY (plant_id) REFERENCES plants (id),
    CONSTRAINT fk_lots_design FOREIGN KEY (design_id) REFERENCES designs (id),
    CONSTRAINT fk_lots_press FOREIGN KEY (press_id) REFERENCES machines (id),
    CONSTRAINT fk_lots_shift FOREIGN KEY (opened_shift_id) REFERENCES shifts (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_lots_list ON production_lots (plant_id, opened_date);
CREATE INDEX ix_lots_press ON production_lots (press_id, status);

-- One shift of one lot at the press and dryer.
CREATE TABLE press_entries (
    id               BIGINT          NOT NULL AUTO_INCREMENT,
    plant_id         BIGINT          NOT NULL,
    entry_no         VARCHAR(40),                     -- UNIT-1/PR/26-27/0001, given when posted
    lot_id           BIGINT          NOT NULL,
    prod_date        DATE            NOT NULL,
    shift_id         BIGINT          NOT NULL,
    press_id         BIGINT          NOT NULL,        -- copied from the lot
    dryer_id         BIGINT,
    silo_id          BIGINT          NOT NULL,        -- where the powder came from
    status           VARCHAR(20)     NOT NULL,        -- DRAFT / POSTED / CANCELLED
    pressed_count    INT             NOT NULL,        -- tiles pressed
    press_rejects    INT             NOT NULL DEFAULT 0,
    dryer_rejects    INT             NOT NULL DEFAULT 0,
    green_weight_kg  DECIMAL(6, 2),                   -- average weight of one green tile
    powder_ton       DECIMAL(10, 3)  NOT NULL,        -- drawn from the silo (pressed x green weight, or typed)
    untraced_ton     DECIMAL(10, 3)  NOT NULL DEFAULT 0,  -- drawn beyond what the silo had on record
    pressure         DECIMAL(6, 1),                   -- specific pressure, kg/cm2
    cycles_per_min   DECIMAL(4, 1),
    thickness_mm     DECIMAL(5, 2),                   -- green thickness
    dryer_temp       DECIMAL(5, 1),                   -- deg C
    dryer_moisture   DECIMAL(4, 2),                   -- % at dryer exit
    remarks          VARCHAR(500),
    posted_at        DATETIME(6),
    posted_by        VARCHAR(100),
    cancelled_at     DATETIME(6),
    cancelled_by     VARCHAR(100),
    cancel_reason    VARCHAR(255),
    created_at       DATETIME(6)     NOT NULL,
    updated_at       DATETIME(6)     NOT NULL,
    created_by       VARCHAR(100),
    updated_by       VARCHAR(100),
    version          BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_press_entries_no UNIQUE (entry_no),
    CONSTRAINT fk_press_plant FOREIGN KEY (plant_id) REFERENCES plants (id),
    CONSTRAINT fk_press_lot FOREIGN KEY (lot_id) REFERENCES production_lots (id),
    CONSTRAINT fk_press_shift FOREIGN KEY (shift_id) REFERENCES shifts (id),
    CONSTRAINT fk_press_press FOREIGN KEY (press_id) REFERENCES machines (id),
    CONSTRAINT fk_press_dryer FOREIGN KEY (dryer_id) REFERENCES machines (id),
    CONSTRAINT fk_press_silo FOREIGN KEY (silo_id) REFERENCES machines (id),
    CONSTRAINT ck_press_counts CHECK (pressed_count > 0 AND press_rejects >= 0 AND dryer_rejects >= 0
                                      AND press_rejects + dryer_rejects <= pressed_count),
    CONSTRAINT ck_press_powder CHECK (powder_ton > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_press_list ON press_entries (plant_id, prod_date);
CREATE INDEX ix_press_lot ON press_entries (lot_id, status);

-- Which spray runs' powder a press entry used, and how much: the link from tiles back to body batches.
CREATE TABLE press_entry_powders (
    id            BIGINT          NOT NULL AUTO_INCREMENT,
    entry_id      BIGINT          NOT NULL,
    spray_run_id  BIGINT          NOT NULL,
    tonnes        DECIMAL(10, 3)  NOT NULL,
    created_at    DATETIME(6)     NOT NULL,
    updated_at    DATETIME(6)     NOT NULL,
    created_by    VARCHAR(100),
    updated_by    VARCHAR(100),
    version       BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_entry_powders_entry FOREIGN KEY (entry_id) REFERENCES press_entries (id) ON DELETE CASCADE,
    CONSTRAINT fk_entry_powders_run FOREIGN KEY (spray_run_id) REFERENCES spray_runs (id),
    CONSTRAINT ck_entry_powders_tonnes CHECK (tonnes > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
