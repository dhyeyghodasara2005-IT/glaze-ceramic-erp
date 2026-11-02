-- =====================================================================
-- V23: Step 10c - the weather, hour by hour.
--
-- Body moisture, drying and pressing all react to the weather, so predictions need
-- it beside the production records. Saved per plant, for the plant's own location.
--
--   reading_hour is plant wall-clock time (Asia/Kolkata), like every other time a
--   supervisor sees: 20:00 means 20:00 at the plant, so it lines up with the shifts
--   in plain SQL and in Python later.
-- =====================================================================

ALTER TABLE plants
    ADD COLUMN latitude  DECIMAL(8, 5),
    ADD COLUMN longitude DECIMAL(8, 5);

CREATE TABLE weather_readings (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    plant_id       BIGINT         NOT NULL,
    reading_hour   DATETIME       NOT NULL,
    temperature_c  DECIMAL(4, 1)  NOT NULL,
    humidity_pct   DECIMAL(4, 1)  NOT NULL,
    rain_mm        DECIMAL(6, 2)  NOT NULL DEFAULT 0,
    created_at     DATETIME(6)    NOT NULL,
    updated_at     DATETIME(6)    NOT NULL,
    created_by     VARCHAR(100),
    updated_by     VARCHAR(100),
    version        BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    -- the weather service may correct a past hour: the same hour is updated, never saved twice
    CONSTRAINT uk_weather_plant_hour UNIQUE (plant_id, reading_hour),
    CONSTRAINT fk_weather_plant FOREIGN KEY (plant_id) REFERENCES plants (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
