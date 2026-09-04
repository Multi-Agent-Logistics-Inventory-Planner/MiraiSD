-- Global break-glass bypass, orthogonal to users.role (ADMIN/ASSISTANT_MANAGER/EMPLOYEE reflect
-- job function; system-admin bypass is a separate axis) - see
-- docs/specs/authentication-and-authorization.md section 2.
ALTER TABLE users ADD COLUMN is_system_admin BOOLEAN NOT NULL DEFAULT FALSE;
