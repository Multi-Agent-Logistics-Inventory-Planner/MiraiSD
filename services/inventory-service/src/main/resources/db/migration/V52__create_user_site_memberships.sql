-- user_site_memberships grants a backend user access to a site. It carries no role column:
-- role is a property of the user (users.role), not the site - see the role model decision in
-- docs/plans/enterprise-modernization.md section 7 and docs/specs/authentication-and-authorization.md
-- section 5.
--
-- sites is not a Flyway-managed table (it is created by infra/init-db/20-unified-locations.sql,
-- a Postgres init script that runs once against a fresh database), so this is the first Flyway
-- migration to take a foreign key dependency on it. Every environment with an existing database
-- - including every deployed environment today - already ran that init script, so the sites
-- table is present. A genuinely fresh environment MUST run infra/init-db/*.sql before Flyway
-- migrates, or this migration's FK will fail; verify that ordering holds wherever this is
-- deployed before merging.
CREATE TABLE user_site_memberships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    site_id UUID NOT NULL REFERENCES sites(id) ON DELETE CASCADE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (user_id, site_id)
);

CREATE INDEX idx_user_site_memberships_user ON user_site_memberships(user_id);
CREATE INDEX idx_user_site_memberships_site_active ON user_site_memberships(site_id, is_active);
