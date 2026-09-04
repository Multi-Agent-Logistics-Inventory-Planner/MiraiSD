-- Second site for multi-site rollout. Placeholder code/name until the business finalizes the
-- real values - safe to rename later via a plain UPDATE migration, since nothing in the codebase
-- hardcodes 'SECOND' (only "MAIN" is referenced, in sites.application.LocationService).
--
-- No memberships are granted here: per docs/specs/authentication-and-authorization.md section 6,
-- only MAIN was backfilled. Access to this site is granted per-user via membership-lifecycle
-- actions (workstream C).
INSERT INTO sites (name, code, country)
VALUES ('Second Location', 'SECOND', 'USA')
ON CONFLICT (code) DO NOTHING;
