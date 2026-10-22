-- =====================================================================
-- V4: Parties - dealers (customers), suppliers and transporters.
-- One table for all three (like Tally ledgers under Sundry Debtors / Creditors):
-- they share GST, address and contact details; each type uses a few extra fields.
-- =====================================================================

CREATE TABLE parties (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    party_type        VARCHAR(20)    NOT NULL,              -- DEALER / SUPPLIER / TRANSPORTER (PartyType.java)
    code              VARCHAR(20)    NOT NULL,              -- DLR-0001 ... (auto when left empty)
    name              VARCHAR(150)   NOT NULL,              -- firm name as on GST certificate
    contact_person    VARCHAR(100),

    -- GST & tax
    gst_registration  VARCHAR(20)    NOT NULL,              -- REGULAR / COMPOSITION / UNREGISTERED / CONSUMER / OVERSEAS
    gstin             VARCHAR(15),                          -- for transporters: Transporter ID used on e-way bills
    pan               VARCHAR(10),

    -- address
    address_line      VARCHAR(255),
    city              VARCHAR(100),
    state             VARCHAR(100),
    state_code        VARCHAR(2),                           -- GST state code, 24 = Gujarat (decides IGST vs CGST+SGST)
    pincode           VARCHAR(10),
    country           VARCHAR(60)    NOT NULL DEFAULT 'India',

    -- contact
    phone             VARCHAR(20),
    mobile            VARCHAR(20),                          -- WhatsApp number
    email             VARCHAR(150),

    -- commercial
    region            VARCHAR(60),                          -- sales area, e.g. "Rajasthan North"
    credit_limit      DECIMAL(14, 2),                       -- dealers: max outstanding allowed
    credit_days       INT,                                  -- dealers: credit period; suppliers: payment days

    -- bank (to pay suppliers / transporters)
    bank_name         VARCHAR(100),
    bank_account_no   VARCHAR(30),
    bank_ifsc         VARCHAR(11),

    notes             VARCHAR(500),
    active            BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at        DATETIME(6)    NOT NULL,
    updated_at        DATETIME(6)    NOT NULL,
    created_by        VARCHAR(100),
    updated_by        VARCHAR(100),
    version           BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_parties_code UNIQUE (code),
    -- the same GSTIN can't be two dealers, but one firm may be both a dealer and a supplier.
    -- (MySQL allows many NULLs in a UNIQUE key, so parties without GSTIN are fine.)
    CONSTRAINT uk_parties_type_gstin UNIQUE (party_type, gstin),
    CONSTRAINT ck_parties_credit_limit CHECK (credit_limit IS NULL OR credit_limit >= 0),
    CONSTRAINT ck_parties_credit_days CHECK (credit_days IS NULL OR credit_days BETWEEN 0 AND 365)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Lists are always filtered by type and sorted by name: one index serves both.
CREATE INDEX ix_parties_type_name ON parties (party_type, name);
