-- Expand/backfill step (docs/specs/multi-site-data-and-api.md section 5): grant every existing
-- user an active membership in the MAIN site, since prior to multi-site all users implicitly had
-- unscoped access. New users after this point get MAIN membership from the application layer
-- (MembershipAuthorizer.grantMainSiteMembershipIfAbsent, called on invitation acceptance), not
-- from a future migration.
--
-- Fail loudly rather than silently backfilling zero rows if the MAIN site row is somehow absent
-- at migration time (it is normally ensured by application code before this ships, but there is
-- no Flyway-managed seed for it - see V52's comment on sites not being Flyway-managed).
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM sites WHERE code = 'MAIN') THEN
        RAISE EXCEPTION 'user_site_memberships backfill requires a MAIN site row to already exist';
    END IF;
END $$;

INSERT INTO user_site_memberships (user_id, site_id, is_active)
SELECT u.id, s.id, TRUE
FROM users u
CROSS JOIN sites s
WHERE s.code = 'MAIN'
ON CONFLICT (user_id, site_id) DO NOTHING;
