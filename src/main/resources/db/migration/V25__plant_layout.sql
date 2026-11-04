-- =====================================================================
-- V25: The plant's layout - which machine feeds which - and replacing a machine.
--
-- Plants in Morbi differ: one line may have two presses taking turns in front of one kiln, another has one press
-- feeding two kilns, and compensators (box storage) sit after the dryer, before the kiln and in other places.
-- So the layout is not built into the code: each plant draws its own with links "A feeds into B".
--
--   PRESS-1 --+
--             +--> DRY-1 --> GL-1 --> DP-1 --> COMP-1 --> KILN-1 --> POL-1 --> SORT-1
--   PRESS-2 --+
--
-- The app uses the links to fill in the obvious machine on an entry (the kiln after this printer) and to keep a
-- lot's trace on its own line. Compensators need no entries: the plant's tile counters already count everything,
-- and the tiles go back on the line to fill gaps.
--
-- No rows are added here: each plant sets its own layout on the Machines screen.
-- =====================================================================

CREATE TABLE machine_links (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    plant_id         BIGINT       NOT NULL,
    from_machine_id  BIGINT       NOT NULL,          -- this machine ...
    to_machine_id    BIGINT       NOT NULL,          -- ... feeds into this one
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    created_by       VARCHAR(100),
    updated_by       VARCHAR(100),
    version          BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_machine_links UNIQUE (from_machine_id, to_machine_id),
    CONSTRAINT fk_machine_link_plant FOREIGN KEY (plant_id) REFERENCES plants (id),
    CONSTRAINT fk_machine_link_from FOREIGN KEY (from_machine_id) REFERENCES machines (id),
    CONSTRAINT fk_machine_link_to FOREIGN KEY (to_machine_id) REFERENCES machines (id),
    CONSTRAINT ck_machine_link_not_itself CHECK (from_machine_id <> to_machine_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_machine_links_plant ON machine_links (plant_id);
CREATE INDEX ix_machine_links_to ON machine_links (to_machine_id);

-- A machine taken out and replaced by a new one points at its replacement. Its past entries, breakdowns and
-- finished preventive jobs stay with it; its place in the layout, its meters and its preventive tasks move.
ALTER TABLE machines
    ADD COLUMN replaced_by_id BIGINT NULL AFTER active,
    ADD CONSTRAINT fk_machines_replaced_by FOREIGN KEY (replaced_by_id) REFERENCES machines (id);
