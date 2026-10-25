-- =====================================================================
-- V19: Step 8d - raw-material lab tests at the GRN.
--
-- From the plant (25 Oct 2026): the lab tests raw material when it arrives,
-- whatever each material needs, and material that fails is rejected.
--
--   lab_parameters  the tests a material gets (moisture, residue ...), with an
--                   acceptable range if the plant has one. A material with
--                   tests set up waits for the lab at every GRN.
--   lab_tests       one per GRN and material: the sample of that truck. The
--                   lab accepts or rejects it; a rejected material's lines on
--                   the GRN are rejected in full, so none of it enters stock.
--   lab_results     the values found, with the range as it was that day.
-- =====================================================================

CREATE TABLE lab_parameters (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    material_id     BIGINT          NOT NULL,
    code            VARCHAR(10)     NOT NULL,          -- MOIST, RES63, WA, FE2O3, GCV ...
    name            VARCHAR(60)     NOT NULL,          -- "Moisture"
    unit            VARCHAR(15),                       -- "%", "kcal/kg", "L*"
    min_value       DECIMAL(12, 3),                    -- the acceptable range; either end may be open
    max_value       DECIMAL(12, 3),
    sort_order      INT             NOT NULL DEFAULT 0,
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      DATETIME(6)     NOT NULL,
    updated_at      DATETIME(6)     NOT NULL,
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    version         BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_lab_parameters_code UNIQUE (material_id, code),
    CONSTRAINT fk_lab_parameter_material FOREIGN KEY (material_id) REFERENCES materials (id),
    CONSTRAINT ck_lab_parameter_range CHECK (min_value IS NULL OR max_value IS NULL OR min_value <= max_value)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE lab_tests (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    plant_id        BIGINT          NOT NULL,
    test_no         VARCHAR(40)     NOT NULL,          -- UNIT-1/LAB/26-27/0001
    document_id     BIGINT          NOT NULL,          -- the GRN
    material_id     BIGINT          NOT NULL,
    tested_on       DATE            NOT NULL,
    tested_by       VARCHAR(150),                      -- the lab staff's names
    verdict         VARCHAR(20)     NOT NULL,          -- ACCEPTED / REJECTED
    out_of_range    INT             NOT NULL DEFAULT 0,   -- how many results fell outside their range
    remarks         VARCHAR(500),
    created_at      DATETIME(6)     NOT NULL,
    updated_at      DATETIME(6)     NOT NULL,
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    version         BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_lab_tests_no UNIQUE (plant_id, test_no),
    CONSTRAINT uk_lab_tests_grn_material UNIQUE (document_id, material_id),
    CONSTRAINT fk_lab_test_plant FOREIGN KEY (plant_id) REFERENCES plants (id),
    -- deleting a draft GRN deletes its tests with it
    CONSTRAINT fk_lab_test_document FOREIGN KEY (document_id) REFERENCES store_documents (id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_test_material FOREIGN KEY (material_id) REFERENCES materials (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_lab_tests_list ON lab_tests (plant_id, tested_on);

CREATE TABLE lab_results (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    test_id         BIGINT          NOT NULL,
    parameter_id    BIGINT          NOT NULL,
    sort_order      INT             NOT NULL,
    -- the test as it was set up that day: a range changed later doesn't rewrite old results
    code            VARCHAR(10)     NOT NULL,
    name            VARCHAR(60)     NOT NULL,
    unit            VARCHAR(15),
    min_value       DECIMAL(12, 3),
    max_value       DECIMAL(12, 3),
    value           DECIMAL(12, 3)  NOT NULL,
    within_range    BOOLEAN         NOT NULL,
    created_at      DATETIME(6)     NOT NULL,
    updated_at      DATETIME(6)     NOT NULL,
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    version         BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_lab_result_test FOREIGN KEY (test_id) REFERENCES lab_tests (id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_result_parameter FOREIGN KEY (parameter_id) REFERENCES lab_parameters (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- Starter roles (same rules as V9-V18: only roles this project created, never the user's own).
-- ---------------------------------------------------------------------
INSERT INTO roles (name, description, system_role, created_at, updated_at, created_by, updated_by)
SELECT 'Lab chemist', 'Starter role: tests raw material when it arrives and accepts or rejects it.', FALSE,
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'Lab chemist');

INSERT INTO role_permissions (role_id, permission)
SELECT r.id, p.permission
FROM roles r
CROSS JOIN (SELECT 'LAB_VIEW' AS permission UNION ALL SELECT 'LAB_ENTRY') p
WHERE r.name = 'Lab chemist' AND r.created_by = 'system' AND r.system_role = FALSE
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission = p.permission);

-- The store keeper sees why a GRN waits; the manager and the viewer see the results.
INSERT INTO role_permissions (role_id, permission)
SELECT r.id, 'LAB_VIEW'
FROM roles r
WHERE r.name IN ('Store keeper', 'Production manager', 'Viewer (read only)')
  AND r.created_by = 'system' AND r.system_role = FALSE
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission = 'LAB_VIEW');
