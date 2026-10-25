-- =====================================================================
-- V18: Step 8c - energy: meters and the fuel the plant burns.
--
-- From the plant (25 Oct 2026):
--   gas    Gujarat Gas main meter (the one the bill is on), read once a day in the morning;
--          a company-fitted meter at the kiln, read after every shift (m3)
--   power  GEB main meter, once a day in the morning; a meter per section, after every shift
--   coal   the spray dryer burns coal, by weight; propane / LNG, when used, by weight too
--   DG     the diesel generator has an hour meter; the diesel it burns is measured
--
-- So a "meter" here is anything energy is written down for. Two ways to write it down:
--   counter  GAS, POWER, RUN_HOURS: the number on the meter; use = this reading - the one before
--   amount   COAL, PROPANE, LNG, DIESEL: the amount used, typed as it is
-- =====================================================================

CREATE TABLE meters (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    plant_id        BIGINT          NOT NULL,
    code            VARCHAR(30)     NOT NULL,          -- GG-MAIN, KILN1-GAS, GEB-MAIN, PRESS-PWR, DG-HRS ...
    name            VARCHAR(100)    NOT NULL,          -- "Gujarat Gas main meter"
    kind            VARCHAR(20)     NOT NULL,          -- GAS, POWER, RUN_HOURS, COAL, PROPANE, LNG, DIESEL
    unit            VARCHAR(10)     NOT NULL,          -- SCM, M3, KWH, HOUR, KG, LITRE (follows the kind)
    main            BOOLEAN         NOT NULL DEFAULT FALSE,   -- the supplier's meter, the one the bill is on
    machine_id      BIGINT,                            -- the machine it measures (kiln, spray dryer, DG set), if one
    read_every      VARCHAR(10)     NOT NULL,          -- DAY (once, the next morning) / SHIFT (after each shift)
    sort_order      INT             NOT NULL DEFAULT 0,
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      DATETIME(6)     NOT NULL,
    updated_at      DATETIME(6)     NOT NULL,
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    version         BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_meters_code UNIQUE (plant_id, code),
    CONSTRAINT fk_meter_plant FOREIGN KEY (plant_id) REFERENCES plants (id),
    CONSTRAINT fk_meter_machine FOREIGN KEY (machine_id) REFERENCES machines (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE meter_readings (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    plant_id        BIGINT          NOT NULL,
    meter_id        BIGINT          NOT NULL,
    -- the production day the reading closes: for a shift, its production date; for a once-a-day meter, the day
    -- before the morning it was read on (read on the 26th at 8:00 -> the 25th)
    read_on         DATE            NOT NULL,
    shift_id        BIGINT,                            -- null for once-a-day meters
    -- one reading per meter and round; NULL shift_ids would never clash in a UNIQUE key, so the key uses 0 for them
    slot            BIGINT AS (COALESCE(shift_id, 0)) STORED,
    reading         DECIMAL(16, 3),                    -- the number on the meter (counter kinds only)
    used            DECIMAL(14, 3)  NOT NULL,          -- this reading - the one before, or the amount typed
    restarted       BOOLEAN         NOT NULL DEFAULT FALSE,   -- meter changed / started again from 0
    note            VARCHAR(255),
    created_at      DATETIME(6)     NOT NULL,
    updated_at      DATETIME(6)     NOT NULL,
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    version         BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_meter_readings_slot UNIQUE (meter_id, read_on, slot),
    CONSTRAINT fk_reading_plant FOREIGN KEY (plant_id) REFERENCES plants (id),
    CONSTRAINT fk_reading_meter FOREIGN KEY (meter_id) REFERENCES meters (id),
    CONSTRAINT fk_reading_shift FOREIGN KEY (shift_id) REFERENCES shifts (id),
    CONSTRAINT ck_reading_values CHECK (used >= 0 AND (reading IS NULL OR reading >= 0))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_meter_readings_day ON meter_readings (plant_id, read_on);
CREATE INDEX ix_meter_readings_meter ON meter_readings (meter_id, read_on);

-- ---------------------------------------------------------------------
-- Starter roles (same rules as V9-V17: only roles this project created, never the user's own).
-- Each supervisor or department in-charge reads their own meters.
-- ---------------------------------------------------------------------
INSERT INTO role_permissions (role_id, permission)
SELECT r.id, 'METER_ENTRY'
FROM roles r
WHERE r.name IN ('Body prep supervisor', 'Press supervisor', 'Glaze supervisor', 'Kiln supervisor', 'Polish supervisor',
                 'Packing supervisor', 'Production manager', 'Maintenance in-charge')
  AND r.created_by = 'system' AND r.system_role = FALSE
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission = 'METER_ENTRY');

INSERT INTO role_permissions (role_id, permission)
SELECT r.id, 'ENERGY_VIEW'
FROM roles r
WHERE r.name IN ('Production manager', 'Maintenance in-charge', 'Viewer (read only)')
  AND r.created_by = 'system' AND r.system_role = FALSE
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission = 'ENERGY_VIEW');
