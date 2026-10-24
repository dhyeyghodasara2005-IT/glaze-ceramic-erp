-- =====================================================================
-- V13: Step 6a - smart put-away.
--
-- Every pillar gets a place in the godown's walk, so Glaze can suggest where
-- new pallets should go (best sellers next to the loading point, one kind of
-- box per pillar) and, in step 6c, pick in the shortest way.
-- =====================================================================

ALTER TABLE fg_pillars
    -- FAST = next to the loading point, NORMAL, SLOW = the far ends
    ADD COLUMN zone        VARCHAR(10)  NOT NULL DEFAULT 'NORMAL' AFTER code,
    -- distance from the loading point in pillars: 1 = right next to it (both wings can have a 1)
    ADD COLUMN walk_order  INT          AFTER zone,
    -- STORAGE = one kind of box; REMAINDER = small leftovers near the loading point; STAGING = a lane for a truck's load
    ADD COLUMN kind        VARCHAR(20)  NOT NULL DEFAULT 'STORAGE' AFTER walk_order,
    ADD CONSTRAINT ck_fg_pillar_walk CHECK (walk_order IS NULL OR walk_order > 0);

-- The warehouse's view of a design: is it a best seller? (Later, step 6f, Glaze works this out
-- from real dispatches; until then the warehouse ticks it.) Kept apart from the design master,
-- which belongs to the product team.
CREATE TABLE fg_design_settings (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    design_id    BIGINT        NOT NULL,
    fast_mover   BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at   DATETIME(6)   NOT NULL,
    updated_at   DATETIME(6)   NOT NULL,
    created_by   VARCHAR(100),
    updated_by   VARCHAR(100),
    version      BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_fg_design_settings UNIQUE (design_id),
    CONSTRAINT fk_fg_design_settings_design FOREIGN KEY (design_id) REFERENCES designs (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- The plant told us a domestic pallet (rack) holds 50 boxes of 600 x 1200. Filled in only where nobody has set it.
UPDATE sizes SET boxes_per_pallet = 50, updated_at = UTC_TIMESTAMP(6), updated_by = 'system'
WHERE code = '600X1200' AND boxes_per_pallet IS NULL;
