-- =====================================================================
-- V21: Step 9b - what a m² costs in a month.
--
--   cost_rates     what a unit of gas, power, coal ... costs, from a date
--   monthly_costs  what isn't counted anywhere else: salaries, demand charges, interest ...
--
-- The rest comes from what Glaze already records: store issues (material),
-- meter readings x these rates (energy), attendance x worker rates (labour).
--
-- Which costs the plant wants in it is still an open question (sheet F,
-- questions 34-36): a cost head left empty simply isn't in the total.
-- =====================================================================

CREATE TABLE cost_rates (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    plant_id        BIGINT          NOT NULL,
    kind            VARCHAR(20)     NOT NULL,          -- GAS, POWER, COAL ... (MeterKind.java)
    unit            VARCHAR(10)     NOT NULL,          -- what one rate buys: SCM, M3, KWH, KG, LITRE
    valid_from      DATE            NOT NULL,          -- in force until the next rate of the same kind and unit
    rate            DECIMAL(12, 4)  NOT NULL,
    note            VARCHAR(255),
    created_at      DATETIME(6)     NOT NULL,
    updated_at      DATETIME(6)     NOT NULL,
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    version         BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_cost_rates UNIQUE (plant_id, kind, unit, valid_from),
    CONSTRAINT fk_cost_rate_plant FOREIGN KEY (plant_id) REFERENCES plants (id),
    CONSTRAINT ck_cost_rate CHECK (rate >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE monthly_costs (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    plant_id        BIGINT          NOT NULL,
    month_start     DATE            NOT NULL,          -- the first day of the month
    head            VARCHAR(20)     NOT NULL,          -- STAFF_SALARY, POWER_FIXED ... (MonthlyCostHead.java)
    amount          DECIMAL(14, 2)  NOT NULL,
    note            VARCHAR(255),
    created_at      DATETIME(6)     NOT NULL,
    updated_at      DATETIME(6)     NOT NULL,
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    version         BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_monthly_costs UNIQUE (plant_id, month_start, head),
    CONSTRAINT fk_monthly_cost_plant FOREIGN KEY (plant_id) REFERENCES plants (id),
    CONSTRAINT ck_monthly_cost CHECK (amount >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- Starter role (same rules as V9-V20: only roles this project created, never the user's own).
-- Money stays with accounts: the production manager and the viewer don't get it by default.
-- ---------------------------------------------------------------------
INSERT INTO roles (name, description, system_role, created_at, updated_at, created_by, updated_by)
SELECT 'Accounts', 'Starter role: keeps the rates and monthly costs, and sees what a m² costs.',
       FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'Accounts');

INSERT INTO role_permissions (role_id, permission)
SELECT r.id, p.permission
FROM roles r
CROSS JOIN (SELECT 'COST_VIEW' AS permission UNION ALL SELECT 'COST_MANAGE' UNION ALL SELECT 'STORE_VIEW'
            UNION ALL SELECT 'ENERGY_VIEW' UNION ALL SELECT 'LABOUR_VIEW') p
WHERE r.name = 'Accounts' AND r.created_by = 'system' AND r.system_role = FALSE
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission = p.permission);
