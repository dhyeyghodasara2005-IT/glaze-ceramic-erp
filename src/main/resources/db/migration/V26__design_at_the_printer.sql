-- =====================================================================
-- V26: The design is chosen at the digital printer, not at the press.
--
-- From the plant (4 Nov 2026): "the presses output is green tiles. the printer machine decides what to print.
-- So how does it matter whether the tiles were from press1 or press2." Designs change at the printer with the
-- orders, at no fixed time.
--
--   before:  design lot = one design on one PRESS; every press entry belonged to a lot
--   after:   design lot = one design on one PRINTER; a press entry is just pieces, powder and the silo
--
-- A lot's trace back to the body (silo -> spray runs -> mill batches -> trucks) now goes through the press
-- entries of the same dates and shifts as the lot's glaze entries, on the presses of the printer's line in the
-- layout (V25) - worked out by the app, nothing is picked by hand.
-- =====================================================================

ALTER TABLE production_lots ADD COLUMN printer_id BIGINT NULL AFTER design_id;

-- Each lot's printer is the one its glaze entries were made on. (In the dev data every lot used exactly one.)
UPDATE production_lots l
    JOIN (SELECT e.lot_id, MIN(e.printer_id) AS printer_id
          FROM line_entries e
          WHERE e.stage = 'GLAZE' AND e.printer_id IS NOT NULL AND e.status <> 'CANCELLED'
          GROUP BY e.lot_id) g ON g.lot_id = l.id
SET l.printer_id = g.printer_id;

-- A plant with lots but no printer at all gets one, so that every lot can have a printer.
INSERT INTO machines (plant_id, code, name, machine_type, active, created_at, updated_at, created_by, updated_by, version)
SELECT DISTINCT l.plant_id, 'DP-V26', 'Digital printer (added when designs moved to the printer)', 'DIGITAL_PRINTER',
       TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0
FROM production_lots l
WHERE l.printer_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM machines m WHERE m.plant_id = l.plant_id AND m.machine_type = 'DIGITAL_PRINTER');

-- Lots that never reached the glaze line: the plant's first printer.
UPDATE production_lots l
SET l.printer_id = (SELECT MIN(m.id) FROM machines m WHERE m.plant_id = l.plant_id AND m.machine_type = 'DIGITAL_PRINTER')
WHERE l.printer_id IS NULL;

ALTER TABLE production_lots
    MODIFY printer_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_lots_printer FOREIGN KEY (printer_id) REFERENCES machines (id);
CREATE INDEX ix_lots_printer ON production_lots (printer_id, status);

-- The lot no longer has a press ...
ALTER TABLE production_lots DROP FOREIGN KEY fk_lots_press;
DROP INDEX ix_lots_press ON production_lots;
ALTER TABLE production_lots DROP COLUMN press_id;

-- ... and a press entry no longer has a lot (it keeps its press_id). The new index serves the shift match.
ALTER TABLE press_entries DROP FOREIGN KEY fk_press_lot;
DROP INDEX ix_press_lot ON press_entries;
ALTER TABLE press_entries DROP COLUMN lot_id;
CREATE INDEX ix_press_shift ON press_entries (plant_id, prod_date, shift_id, status);

-- Starting and closing designs is now the glaze & print supervisor's job: the lot endpoints ask for GLAZE_ENTRY
-- instead of PRESS_ENTRY. The starter roles already have the right permissions, so no role changes here.
