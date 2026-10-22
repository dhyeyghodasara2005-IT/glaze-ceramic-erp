-- =====================================================================
-- V9: Ready-made starter roles, one per job, so new users can be given a
-- sensible role straight away. They are ordinary roles: rename, change or
-- delete them under Setup -> Roles.
--
-- Safe to run on a database that is already in use:
--   * a role whose name already exists is skipped (never overwritten);
--   * permissions are only added to roles THIS script created
--     (created_by 'system', not the Super Admin, and still without permissions).
-- =====================================================================

INSERT INTO roles (name, description, system_role, created_at, updated_at, created_by, updated_by)
SELECT s.name, s.description, FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'
FROM (
    SELECT 'Store keeper' AS name,
           'Starter role: receives (GRN), issues and adjusts store stock, manages godowns.' AS description
    UNION ALL SELECT 'Body prep supervisor',
           'Starter role: ball mill charges, slip tests, spray dryer runs.'
    UNION ALL SELECT 'Press supervisor',
           'Starter role: starts design lots, press & dryer shift entries.'
    UNION ALL SELECT 'Glaze supervisor',
           'Starter role: glaze line and digital printer shift entries.'
    UNION ALL SELECT 'Kiln supervisor',
           'Starter role: kiln shift entries.'
    UNION ALL SELECT 'Production manager',
           'Starter role: every production division, can cancel posted production entries.'
    UNION ALL SELECT 'Viewer (read only)',
           'Starter role: sees companies, plants, masters, parties, store and production; changes nothing.'
) s
WHERE NOT EXISTS (SELECT 1 FROM roles r WHERE r.name = s.name);

INSERT INTO role_permissions (role_id, permission)
SELECT r.id, p.permission
FROM roles r
JOIN (
    SELECT 'Store keeper' AS role_name, 'STORE_VIEW' AS permission
    UNION ALL SELECT 'Store keeper', 'STORE_ENTRY'

    UNION ALL SELECT 'Body prep supervisor', 'PRODUCTION_VIEW'
    UNION ALL SELECT 'Body prep supervisor', 'BODY_PREP_ENTRY'

    UNION ALL SELECT 'Press supervisor', 'PRODUCTION_VIEW'
    UNION ALL SELECT 'Press supervisor', 'PRESS_ENTRY'

    UNION ALL SELECT 'Glaze supervisor', 'PRODUCTION_VIEW'
    UNION ALL SELECT 'Glaze supervisor', 'GLAZE_ENTRY'

    UNION ALL SELECT 'Kiln supervisor', 'PRODUCTION_VIEW'
    UNION ALL SELECT 'Kiln supervisor', 'KILN_ENTRY'

    UNION ALL SELECT 'Production manager', 'PRODUCTION_VIEW'
    UNION ALL SELECT 'Production manager', 'BODY_PREP_ENTRY'
    UNION ALL SELECT 'Production manager', 'PRESS_ENTRY'
    UNION ALL SELECT 'Production manager', 'GLAZE_ENTRY'
    UNION ALL SELECT 'Production manager', 'KILN_ENTRY'
    UNION ALL SELECT 'Production manager', 'PRODUCTION_CANCEL'
    UNION ALL SELECT 'Production manager', 'STORE_VIEW'
    UNION ALL SELECT 'Production manager', 'MASTER_VIEW'

    UNION ALL SELECT 'Viewer (read only)', 'COMPANY_VIEW'
    UNION ALL SELECT 'Viewer (read only)', 'PLANT_VIEW'
    UNION ALL SELECT 'Viewer (read only)', 'MASTER_VIEW'
    UNION ALL SELECT 'Viewer (read only)', 'PARTY_VIEW'
    UNION ALL SELECT 'Viewer (read only)', 'STORE_VIEW'
    UNION ALL SELECT 'Viewer (read only)', 'PRODUCTION_VIEW'
) p ON p.role_name = r.name
WHERE r.created_by = 'system'
  AND r.system_role = FALSE
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id);
