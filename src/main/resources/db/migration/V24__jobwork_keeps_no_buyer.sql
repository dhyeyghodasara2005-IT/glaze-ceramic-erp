-- =====================================================================
-- V24: On jobwork the goods are sold to the principal. Who buys them next, and
-- where they are sold, isn't known to the plant and isn't kept.
--
-- A jobwork order already has no field for it. These are the two free-text
-- places it could still have been typed: a principal's sales area, and the
-- order's narration. The CHECKs hold the database to it whatever the code does.
-- =====================================================================

UPDATE parties SET region = NULL WHERE party_type = 'JOBWORK';
UPDATE sales_orders SET remarks = NULL WHERE order_type = 'JOBWORK';

ALTER TABLE parties
    ADD CONSTRAINT ck_party_jobwork_no_region CHECK (party_type <> 'JOBWORK' OR region IS NULL);
ALTER TABLE sales_orders
    ADD CONSTRAINT ck_so_jobwork_no_remarks CHECK (order_type <> 'JOBWORK' OR remarks IS NULL);
