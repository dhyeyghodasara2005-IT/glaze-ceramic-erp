-- =====================================================================
-- V1: Foundation - companies, plants, roles, permissions, users.
-- Flyway runs each V<n>__*.sql file exactly once, in order.
-- NEVER edit a migration after it has run; add a new V<n+1> file instead.
-- =====================================================================

-- Every business table has these audit columns (see BaseEntity.java):
--   created_at / updated_at  : when (UTC)
--   created_by / updated_by  : who (username)
--   version                  : optimistic locking counter

-- A legal entity (firm) with its own GSTIN. One installation can hold many.
CREATE TABLE companies (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    code          VARCHAR(20)  NOT NULL,
    name          VARCHAR(150) NOT NULL,
    legal_name    VARCHAR(200),
    gstin         VARCHAR(15),
    pan           VARCHAR(10),
    address_line  VARCHAR(255),
    city          VARCHAR(100),
    state         VARCHAR(100),
    pincode       VARCHAR(6),
    phone         VARCHAR(20),
    email         VARCHAR(150),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    created_by    VARCHAR(100),
    updated_by    VARCHAR(100),
    version       BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_companies_code UNIQUE (code),
    CONSTRAINT uk_companies_gstin UNIQUE (gstin)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- A manufacturing unit belonging to a company. Almost every future record
-- (production, stock, dispatch) will carry a plant_id.
CREATE TABLE plants (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    company_id            BIGINT        NOT NULL,
    code                  VARCHAR(20)   NOT NULL,
    name                  VARCHAR(150)  NOT NULL,
    gstin                 VARCHAR(15),
    address_line          VARCHAR(255),
    city                  VARCHAR(100),
    state                 VARCHAR(100),
    pincode               VARCHAR(6),
    capacity_sqm_per_day  DECIMAL(12, 2),
    active                BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at            DATETIME(6)   NOT NULL,
    updated_at            DATETIME(6)   NOT NULL,
    created_by            VARCHAR(100),
    updated_by            VARCHAR(100),
    version               BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_plants_code UNIQUE (code),
    CONSTRAINT fk_plants_company FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- A role is a named bundle of permissions, created by the admin from the UI
-- (e.g. "Kiln Supervisor", "Dispatch Clerk").
CREATE TABLE roles (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    name         VARCHAR(80)   NOT NULL,
    description  VARCHAR(255),
    system_role  BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at   DATETIME(6)   NOT NULL,
    updated_at   DATETIME(6)   NOT NULL,
    created_by   VARCHAR(100),
    updated_by   VARCHAR(100),
    version      BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_roles_name UNIQUE (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Permissions are fixed in code (Permission.java); this table stores which
-- ones each role has.
CREATE TABLE role_permissions (
    role_id     BIGINT       NOT NULL,
    permission  VARCHAR(60)  NOT NULL,
    PRIMARY KEY (role_id, permission),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Named app_users (not "users") to avoid clashing with SQL keywords in other databases.
CREATE TABLE app_users (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    username              VARCHAR(50)   NOT NULL,
    full_name             VARCHAR(150)  NOT NULL,
    phone                 VARCHAR(20),
    email                 VARCHAR(150),
    password_hash         VARCHAR(100)  NOT NULL,
    active                BOOLEAN       NOT NULL DEFAULT TRUE,
    must_change_password  BOOLEAN       NOT NULL DEFAULT TRUE,
    all_plants_access     BOOLEAN       NOT NULL DEFAULT FALSE,
    preferred_language    VARCHAR(5)    NOT NULL DEFAULT 'en',
    last_login_at         DATETIME(6),
    created_at            DATETIME(6)   NOT NULL,
    updated_at            DATETIME(6)   NOT NULL,
    created_by            VARCHAR(100),
    updated_by            VARCHAR(100),
    version               BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_app_users_username UNIQUE (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE user_roles (
    user_id  BIGINT NOT NULL,
    role_id  BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES app_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Which plants a user may see/work in (ignored when all_plants_access = TRUE).
CREATE TABLE user_plants (
    user_id   BIGINT NOT NULL,
    plant_id  BIGINT NOT NULL,
    PRIMARY KEY (user_id, plant_id),
    CONSTRAINT fk_user_plants_user FOREIGN KEY (user_id) REFERENCES app_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_plants_plant FOREIGN KEY (plant_id) REFERENCES plants (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
