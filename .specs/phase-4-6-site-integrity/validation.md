# Validation

## Command and scope

`./mvnw -B clean test -Dtest=SiteIntegrityMigrationIT`

## Result

Pass — `./mvnw -B clean test -Dtest=SiteIntegrityMigrationIT` passed 3 PostgreSQL Testcontainers tests, then `./mvnw -B clean test -Dtest='*IT'` completed with no failing or error integration reports. Both migrations are applied only to disposable test schemas.

## Acceptance criteria evidence

- AC-1: SECOND receives one NOT_ASSIGNED storage location and one `NA` location; MAIN is untouched.
- AC-2: concurrent duplicate insert and storage-location reparenting are rejected.
- AC-3: V67 leaves NULL writers compatible; V68 rejects NULL and orphan sites. Production V68 remains deferred until post-deploy NULL verification.
