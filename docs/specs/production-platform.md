# Lean Production Platform Specification

- Status: Draft
- Date: 2026-08-12
- Scope: GHCR artifacts, one Hetzner host, deployment, rollback and recovery
- Roadmap: [Multi-site modernization](../roadmap/enterprise-modernization.md)

## 1. Availability and cost statement

Initial production uses one Hetzner host while Supabase provides PostgreSQL and authentication. The
host is a compute availability boundary and is not highly available. The complete recurring stack
MUST remain within the approved approximately $300/month ceiling.

Every paid addition records expected monthly cost, one-time migration/overlap cost, free-tier limit,
budget owner and removal path before approval.

## 2. Active technology scope

The initial platform uses:

- GitHub Actions for tests and image builds;
- GHCR for versioned Docker images;
- one Hetzner host;
- existing Docker Compose and Caddy configuration;
- Supabase PostgreSQL/auth and its included production backups;
- existing structured container/application logs;
- a basic external uptime check.

Terraform, Ansible, SOPS, Redis, full OpenTelemetry, Grafana Cloud, Sentry, load balancers and extra
hosts are optional follow-up decisions, not initial deployment requirements.

## 3. Host and network

- Select capacity from measured CPU/memory/disk usage with enough headroom for Spring, both Python
  workers, Kafka, Caddy and any deployed Next.js runtime.
- Only Caddy HTTP/HTTPS ports and tightly restricted administration access are public.
- APIs, Kafka, workers and administrative endpoints remain private or localhost-bound.
- Persistent Kafka data uses a durable volume with disk monitoring.
- Firewall and DNS configuration are documented even if initially managed through the provider UI.

The web application is not treated as static files while Next.js server routes remain.

## 4. GHCR build artifacts

CI builds and tests each changed deployable, then pushes versioned images to GHCR. Production deploys
immutable digests rather than mutable tags alone. Images record source revision and build metadata.
Production MUST NOT build from a mutable Git checkout.

Container vulnerability scanning uses available CI capabilities. Image retention keeps current and
previous known-good production images while pruning obsolete branch/PR images. GitHub Actions and
package budgets prevent uncontrolled usage.

## 5. Secrets

Secrets never enter Git, images, client bundles or build logs. Initially they MAY be provided by a
protected CI environment and a root-restricted production environment file. Rotation and emergency
revocation are documented.

SOPS or another encrypted-configuration system becomes appropriate when multiple hosts/operators or
manual secret distribution creates material risk.

## 6. Migration, deployment and rollback

The deployment process:

1. Records the approved image digests.
2. Verifies required configuration without printing values.
3. Runs Flyway migration gating.
4. Pulls images and starts services with health checks.
5. Executes authenticated and unauthenticated smoke tests.
6. Retains the previous compatible digest set for rollback.
7. Restores that set if health or smoke tests fail.

Expand/contract database migrations preserve compatibility with the running and rollback versions.
Rollback procedures distinguish application rollback from database restoration.

## 7. DigitalOcean cutover

DigitalOcean and Hetzner overlap only for a documented stabilization window, normally 2–4 weeks.
Before cutover, the runbook defines DNS behavior, data-write ownership, rollback conditions and the
point after which database restoration—not DNS reversal—is required.

The DigitalOcean shutdown date is part of cutover approval so temporary double billing cannot become
indefinite.

## 8. Minimum observability

Initial operations require:

- structured logs with correlation, site and event context where applicable;
- container health/restart visibility;
- disk, memory and basic HTTP health monitoring;
- external uptime checking from outside the Hetzner host;
- visible outbox age, consumer failure/dead-letter and scheduler failure signals;
- concise deploy, rollback, restore and host-loss runbooks.

Paid telemetry or error-monitoring products are introduced only when this baseline cannot meet an
identified incident-response need. High-cardinality or excessive telemetry is prohibited regardless
of provider.

## 9. Backup and recovery

- Use the backups included with the selected Supabase production plan initially.
- Define and approve recovery-time and recovery-point objectives.
- Back up independently recoverable configuration and deployment manifests.
- Protect persistent Hetzner/Kafka state according to its business value.
- Rehearse restoration before cutover and at least quarterly afterward.

Supabase PITR is not assumed. It requires a separate decision if included backups cannot meet the
approved recovery-point objective. A backup is not accepted until restoration succeeds.

## 10. Acceptance criteria

- Production runs digest-pinned GHCR images and never builds application source on the host.
- A failed deployment can return to the previous compatible digest set.
- External monitoring detects loss of the sole host.
- A documented restoration rehearsal succeeds.
- DigitalOcean overlap is time-boxed and closed.
- Actual recurring cost remains within the approved ceiling.
- The platform is accurately documented as single-host, not highly available.

## 11. Deferred platform triggers

| Technology/capability | Trigger |
| --- | --- |
| Terraform/Ansible | Recreating or changing the host manually is no longer reliably reviewable |
| SOPS | Secret distribution involves multiple hosts/operators or unsafe manual repetition |
| OpenTelemetry/Grafana/Sentry | Current logs and uptime checks cannot answer an identified operational question |
| Redis | Multiple API processes require shared coordination/rate limiting |
| Second host/load balancer | Approved availability target or capacity evidence |
| Supabase PITR | Approved RPO is shorter than included-backup recovery permits |
