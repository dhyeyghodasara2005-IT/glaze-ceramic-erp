-- =====================================================================
-- V22: Step 10a - raw material tracked lot by lot.
--
-- Until now a godown's stock was one pool: nobody could say which truck of clay
-- went into which mill charge. Predictions need that link, and it can't be
-- rebuilt later, so it starts now.
--
--   material_lots       one per GRN line (a truck of one material). Stock added
--                       by an adjustment is a lot too, so the lots of a godown
--                       always add up to its stock_balances quantity.
--   material_lot_draws  "this stock movement took 12 T from that lot". Taken
--                       oldest first, like powder from a silo.
--
-- Only quantities are traced. The VALUE of stock stays the weighted average per
-- godown, so the cost figures don't change.
-- =====================================================================

CREATE TABLE material_lots (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    plant_id        BIGINT          NOT NULL,
    material_id     BIGINT          NOT NULL,
    location_id     BIGINT          NOT NULL,
    lot_no          VARCHAR(50)     NOT NULL,          -- the document number and line: UNIT-1/GRN/26-27/0001-2
    received_on     DATE            NOT NULL,
    supplier_id     BIGINT,                            -- GRN only
    -- what made it; all three are empty for stock that was there before lots began
    document_id     BIGINT,
    line_id         BIGINT,
    transaction_id  BIGINT,
    quantity_in     DECIMAL(16, 3)  NOT NULL,
    quantity_left   DECIMAL(16, 3)  NOT NULL,
    rate            DECIMAL(14, 4)  NOT NULL,          -- the rate it came in at (for reference; stock is valued at the average)
    cancelled       BOOLEAN         NOT NULL DEFAULT FALSE,   -- its GRN or adjustment was cancelled
    created_at      DATETIME(6)     NOT NULL,
    updated_at      DATETIME(6)     NOT NULL,
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    version         BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_material_lots_no UNIQUE (lot_no),
    CONSTRAINT fk_material_lot_plant FOREIGN KEY (plant_id) REFERENCES plants (id),
    CONSTRAINT fk_material_lot_material FOREIGN KEY (material_id) REFERENCES materials (id),
    CONSTRAINT fk_material_lot_location FOREIGN KEY (location_id) REFERENCES store_locations (id),
    CONSTRAINT fk_material_lot_supplier FOREIGN KEY (supplier_id) REFERENCES parties (id),
    CONSTRAINT fk_material_lot_document FOREIGN KEY (document_id) REFERENCES store_documents (id),
    CONSTRAINT fk_material_lot_line FOREIGN KEY (line_id) REFERENCES store_document_lines (id),
    CONSTRAINT fk_material_lot_txn FOREIGN KEY (transaction_id) REFERENCES stock_transactions (id),
    CONSTRAINT ck_material_lot_left CHECK (quantity_left >= 0 AND quantity_left <= quantity_in)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- the oldest open lot of a material in a godown
CREATE INDEX ix_material_lots_fifo ON material_lots (location_id, material_id, received_on, id);
CREATE INDEX ix_material_lots_list ON material_lots (plant_id, received_on);

CREATE TABLE material_lot_draws (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    lot_id          BIGINT          NOT NULL,
    transaction_id  BIGINT          NOT NULL,          -- the stock movement that took it
    quantity        DECIMAL(16, 3)  NOT NULL,
    reversed        BOOLEAN         NOT NULL DEFAULT FALSE,   -- its document was cancelled and the quantity went back
    created_at      DATETIME(6)     NOT NULL,
    updated_at      DATETIME(6)     NOT NULL,
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    version         BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_lot_draw_lot FOREIGN KEY (lot_id) REFERENCES material_lots (id),
    CONSTRAINT fk_lot_draw_txn FOREIGN KEY (transaction_id) REFERENCES stock_transactions (id),
    CONSTRAINT ck_lot_draw_quantity CHECK (quantity > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Stock already in the godowns becomes one lot each, "old stock": which truck it came from is not known.
-- Dated at the godown's first movement of that material, so it is used before anything received from now on.
INSERT INTO material_lots (plant_id, material_id, location_id, lot_no, received_on, quantity_in, quantity_left, rate,
                           created_at, updated_at, created_by, updated_by)
SELECT b.plant_id, b.material_id, b.location_id, CONCAT('OLD-', b.id),
       COALESCE((SELECT MIN(t.txn_date) FROM stock_transactions t
                 WHERE t.location_id = b.location_id AND t.material_id = b.material_id), UTC_DATE()),
       b.quantity, b.quantity, ROUND(b.value / b.quantity, 4),
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'
FROM stock_balances b
WHERE b.quantity > 0;
