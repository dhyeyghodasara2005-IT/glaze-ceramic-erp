-- =====================================================================
-- V3: Store & production masters - units, material groups, materials, machines.
-- Units, groups and materials are shared by all plants; machines belong to one plant.
-- =====================================================================

-- Unit of measure. decimals = how many decimal places a quantity may have
-- (KG 3 -> 12.345 kg; BOX 0 -> whole boxes only).
CREATE TABLE units (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(10)  NOT NULL,
    name        VARCHAR(40)  NOT NULL,
    decimals    INT          NOT NULL DEFAULT 2,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    created_by  VARCHAR(100),
    updated_by  VARCHAR(100),
    version     BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_units_code UNIQUE (code),
    CONSTRAINT ck_units_decimals CHECK (decimals BETWEEN 0 AND 4)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Grouping of purchased materials (body, glaze, inks, packing, fuel ...).
CREATE TABLE material_groups (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(20)  NOT NULL,
    name        VARCHAR(80)  NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    created_by  VARCHAR(100),
    updated_by  VARCHAR(100),
    version     BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_material_groups_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Anything the factory buys and stores: clay, feldspar, frit, ink, boxes, gas, spares ...
-- reorder_level = warn when stock falls below this (used by the store module).
CREATE TABLE materials (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    code           VARCHAR(30)    NOT NULL,
    name           VARCHAR(120)   NOT NULL,
    group_id       BIGINT         NOT NULL,
    unit_id        BIGINT         NOT NULL,
    hsn_code       VARCHAR(8),
    gst_rate       DECIMAL(5, 2),
    reorder_level  DECIMAL(14, 3),
    description    VARCHAR(255),
    active         BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at     DATETIME(6)    NOT NULL,
    updated_at     DATETIME(6)    NOT NULL,
    created_by     VARCHAR(100),
    updated_by     VARCHAR(100),
    version        BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_materials_code UNIQUE (code),
    CONSTRAINT fk_materials_group FOREIGN KEY (group_id) REFERENCES material_groups (id),
    CONSTRAINT fk_materials_unit FOREIGN KEY (unit_id) REFERENCES units (id),
    CONSTRAINT ck_materials_gst CHECK (gst_rate IS NULL OR (gst_rate >= 0 AND gst_rate <= 100)),
    CONSTRAINT ck_materials_reorder CHECK (reorder_level IS NULL OR reorder_level >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- A machine or line in ONE plant: ball mill, spray dryer, press, kiln, printer, sorting line ...
-- machine_type is one of the values in MachineType.java. Codes are unique within a plant
-- (both plants may have a "PRESS-1").
CREATE TABLE machines (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    plant_id       BIGINT         NOT NULL,
    code           VARCHAR(20)    NOT NULL,
    name           VARCHAR(120)   NOT NULL,
    machine_type   VARCHAR(30)    NOT NULL,
    capacity       DECIMAL(14, 2),
    capacity_unit  VARCHAR(30),
    make           VARCHAR(80),
    model          VARCHAR(80),
    installed_on   DATE,
    active         BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at     DATETIME(6)    NOT NULL,
    updated_at     DATETIME(6)    NOT NULL,
    created_by     VARCHAR(100),
    updated_by     VARCHAR(100),
    version        BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_machines_plant_code UNIQUE (plant_id, code),
    CONSTRAINT fk_machines_plant FOREIGN KEY (plant_id) REFERENCES plants (id),
    CONSTRAINT ck_machines_capacity CHECK (capacity IS NULL OR capacity >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- Starting data
-- ---------------------------------------------------------------------
INSERT INTO units (code, name, decimals, active, created_at, updated_at, created_by, updated_by)
VALUES ('KG',   'Kilogram',             3, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
       ('TON',  'Metric ton',           3, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
       ('LTR',  'Litre',                2, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
       ('SCM',  'Standard cubic metre', 2, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
       ('KWH',  'Kilowatt hour',        2, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
       ('SQM',  'Square metre',         2, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
       ('MTR',  'Metre',                2, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
       ('PCS',  'Pieces',               0, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
       ('NOS',  'Numbers',              0, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
       ('BOX',  'Box',                  0, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
       ('BAG',  'Bag',                  0, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
       ('ROLL', 'Roll',                 0, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system');

INSERT INTO material_groups (code, name, active, created_at, updated_at, created_by, updated_by)
VALUES ('BODY',     'Body raw materials (clay, feldspar, quartz)', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
       ('GLAZE',    'Glaze, engobe & frit',                        TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
       ('INK',      'Digital inks',                                TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
       ('PACKING',  'Packing material',                            TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
       ('FUEL',     'Fuel & gas',                                  TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
       ('CHEMICAL', 'Chemicals & additives',                       TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
       ('SPARE',    'Spares & consumables',                        TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system');
