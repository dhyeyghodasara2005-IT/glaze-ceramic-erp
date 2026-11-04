-- =====================================================================
-- V27: who sees what (4 Nov 2026, from the owner).
--
--   "limit attendance marking to labour department only"
--   "each supervisor has just the necessary information visible ... The full dashboard should only be visible to
--    the accounts and owner."
--
-- 1. DASHBOARD_VIEW: the whole-plant dashboard, money included. The owner's Super Admin role gets every permission
--    by itself at start-up (BootstrapDataInitializer); Accounts gets it here. Everyone else starts on the screen of
--    their own section instead (the menu decides which).
-- 2. Attendance is marked by the Labour office only: the section supervisors lose ATTENDANCE_ENTRY.
-- 3. The "Viewer (read only)" starter role is removed: the plant doesn't need it.
--
-- Same rule as V9-V20: only the starter roles this project created are touched, never a role the admin made.
-- =====================================================================

INSERT INTO role_permissions (role_id, permission)
SELECT r.id, 'DASHBOARD_VIEW'
FROM roles r
WHERE r.name = 'Accounts' AND r.created_by = 'system' AND r.system_role = FALSE
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission = 'DASHBOARD_VIEW');

DELETE rp FROM role_permissions rp
    JOIN roles r ON r.id = rp.role_id
WHERE rp.permission = 'ATTENDANCE_ENTRY'
  AND r.name <> 'Labour office' AND r.created_by = 'system' AND r.system_role = FALSE;

-- The Viewer role: its permissions, who had it, then the role. A user who had only this role keeps their login
-- but can open nothing until the admin gives them another role.
DELETE rp FROM role_permissions rp
    JOIN roles r ON r.id = rp.role_id
WHERE r.name = 'Viewer (read only)' AND r.created_by = 'system' AND r.system_role = FALSE;
DELETE ur FROM user_roles ur
    JOIN roles r ON r.id = ur.role_id
WHERE r.name = 'Viewer (read only)' AND r.created_by = 'system' AND r.system_role = FALSE;
DELETE FROM roles
WHERE name = 'Viewer (read only)' AND created_by = 'system' AND system_role = FALSE;
