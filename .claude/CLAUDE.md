# Mirai Arcade Inventory System — CLAUDE.md

## Project Overview

Inventory management system for an arcade/collectible shop organization. Tracks products across multiple location types (racks, cabinets, box bins, claw machines), runs demand forecasting, manages kuji (lucky draw) boxes, handles shipments, and provides real-time analytics dashboards.

The system is mid-migration from single-site to **multi-site**: one organization operating two (eventually more) physical sites, with product identity global and operational state (inventory, locations, shipments, kuji/lootbox, reviews, notifications, audit) owned per-site. See `docs/README.md` for the full modernization document set — the roadmap, ADR-0001 (domain-modular Spring monolith), and the specs under `docs/specs/` are the durable source of truth for this migration; this file only summarizes current state.

## Tech Stack

### Frontend
- **Next.js** 16.1.6, **React** 19.2, **TypeScript** 5.9
- **TanStack Query** 5.90 (server state), **Tailwind CSS** 4.1, **Radix UI**
- **Vitest** (unit), **Playwright** (E2E)

### Backend — Inventory Service (Java)
- **Spring Boot** 3.5.7, **Java 21**, Maven
- **JPA/Hibernate** + PostgreSQL (Supabase pooled connection)
- **Apache Kafka** 3.8.1 (KRaft mode) for event streaming
- **Caffeine** caching, **MapStruct** DTOs, **Testcontainers** for tests
- Organized as a domain-modular monolith (ADR-0001): one deployment/process/transaction boundary, business-domain modules with enforced dependency rules (see `services/inventory-service/src/main/java/com/mirai/inventoryservice/{catalog,sites,identity,inventory,transfers,shipments,displays,kuji,lootbox,analytics,notifications,reviews,audit,shared}`)

### Contracts
- `packages/contracts/openapi.json` — generated OpenAPI spec, source of truth for the REST contract
- `packages/api-client` — generated TypeScript client/types (`src/schema.d.ts`) consumed by the frontend
- REST endpoint changes require regenerating both; see `docs/sdd-workflow.md`

### Backend — Forecasting Service (Python)
- **FastAPI** 0.115, **Prophet** 1.1.5, **scikit-learn** 1.5, **Pandas** 2.2
- Kafka consumer: receives inventory-change events, reruns forecasts
- Daily cron recompute at midnight UTC

### Backend — Messaging Service (Python)
- **FastAPI** 0.115, **APScheduler** 3.10, Slack webhook integration
- Polls Kafka for low-stock / alert events and sends Slack notifications

### Infrastructure
- **Database**: Supabase PostgreSQL
- **Storage**: Cloudflare R2 (product images); served via `/cdn-cgi/image` transforms — do NOT route through Vercel image optimization
- **Auth**: Supabase JWT
- **Message Queue**: Apache Kafka (KRaft)
- **Reverse Proxy**: Caddy 2.8
- **Deployment**: Docker Compose on a Hetzner CPX21 (3 vCPU / 4 GB / 80 GB); JVM heap capped at 512 MB for inventory service
- **Frontend**: Vercel (analytics + speed insights enabled)

## File Structure

```
MiraiSD/
├── apps/
│   └── web/                        # Next.js frontend
│       └── src/
│           ├── app/                # App Router routes: (auth), (dashboard)/{products,shipments,storage,team,analytics,reviews,notifications,audit-log,settings,yixin}
│           ├── components/         # Feature UI components
│           │   ├── kuji/           # Lucky-draw box system
│           │   ├── lootbox/        # Product crate/bundle system
│           │   ├── shipments/      # Shipment + EasyPost integration
│           │   ├── stock/          # Adjust/transfer inventory
│           │   ├── products/       # Product catalog
│           │   ├── analytics/      # Dashboards & charts
│           │   ├── reviews/        # Customer review tracking
│           │   ├── machine-displays/# Vending/claw machine display mgmt
│           │   ├── suppliers/      # Supplier management
│           │   ├── locations/      # Storage location management (multi-site)
│           │   ├── team/           # Member invite/edit, membership lifecycle
│           │   ├── audit-log/      # Audit trail views
│           │   ├── notifications/  # In-app notifications
│           │   ├── rbac/           # Role-gated UI wrappers
│           │   └── ui/             # Shared design-system components
│           ├── hooks/              # Custom React hooks (queries + mutations)
│           ├── lib/
│           │   ├── api/            # Typed API client modules (one per domain, apps/web/src/lib/api/)
│           │   ├── r2/             # Cloudflare R2 image upload helpers
│           │   ├── supabase/       # Auth & realtime utilities
│           │   ├── rbac/           # Role-based access control helpers (permissions enforced server-side; frontend mirrors for UX)
│           │   └── middleware/     # Route guards
│           └── types/              # Shared TypeScript types (api.ts, etc.)
├── packages/
│   ├── contracts/                  # openapi.json — generated REST contract, source of truth
│   └── api-client/                 # Generated TS client/types consumed by apps/web
├── services/
│   ├── inventory-service/          # Spring Boot REST API — domain-modular monolith (ADR-0001)
│   │   └── src/main/java/com/mirai/inventoryservice/
│   │       ├── catalog/ sites/ identity/ inventory/ transfers/ shipments/
│   │       ├── displays/ kuji/ lootbox/ analytics/ notifications/ reviews/ audit/ shared/
│   │       ├── controllers/ services/ repositories/ models/ dtos/  # legacy technical-layer packages, being migrated into the domain modules above
│   │       ├── auth/                # JWT filter, role/site authorization
│   │       └── kafka/               # Event producers
│   ├── forecasting-service/        # Python forecasting pipeline
│   └── messaging-service/          # Python Slack alert service
├── infra/
│   ├── docker-compose.yml / docker-compose.dev.yml
│   ├── Caddyfile
│   └── db/migrations/              # Flyway SQL migrations (Vxx__ naming)
├── docs/                           # Durable architecture record: specs/, adr/, plans/, roadmap/, baseline/, runbooks/, templates/, sdd-workflow.md
├── .specs/<feature-id>/            # Per-feature SDD execution records (spec.md, log.md, review.md, validation.md)
├── scripts/                        # One-off maintenance scripts (backtests, seeding, MSRP updates, etc.)
├── tests/                          # contracts/ and e2e/ test suites
├── AGENTS.md                       # Agent instructions: sources of truth and SDD lifecycle rules
└── refs/                           # Design docs, forecasting/egress investigation notes, handoffs
```

## Key Design Decisions

1. **Single-store scale**: ~85 days of meaningful history, bursty demand (CV 1.5–2.5). Prefer small-store forecasting techniques over enterprise ML. Backtest + env-var rollback is sufficient for validation.

2. **Event-driven forecasting**: Inventory mutations publish Kafka events → forecasting service recomputes predictions. A 30-second window + 50-item trigger batches rapid bursts. DLQ handles failed messages.

3. **Image storage**: R2 + Cloudflare `/cdn-cgi/image` transforms. Never route product images through Vercel's image optimization pipeline.

4. **Cost ceiling**: Total monthly infra target ~$300. Flag stack-wide cost impact when proposing paid features or new services.

5. **Immutability**: Always return new copies of objects/arrays — never mutate in place.

6. **Role-based access**: Three roles — ADMIN, ASSISTANT_MANAGER, EMPLOYEE. Structural kuji operations (open/close/reopen box, manage tiers) require ADMIN or ASSISTANT_MANAGER. Authorization is enforced backend-side (`services/inventory-service/.../auth`, `identity`); frontend RBAC (`apps/web/src/lib/rbac`) mirrors it for UX only and is not a security boundary.

7. **Kuji box lifecycle**: OPEN → CLOSED (close box) → can be reopened. Products with an active (OPEN) box appear in the Active kuji tab; products without one appear in the Closed tab. `hasActiveBox` is populated server-side on root-product list endpoints.

8. **Multi-site tenancy** (in progress, see `docs/specs/multi-site-data-and-api.md`): one organization, multiple physical sites. Products have one global identity; a `site_products` record keyed by `(site_id, product_id)` owns assortment status, local price/cost overrides, reorder policy, and forecasting config. Locations, inventory, stock movements, shipments, machine displays, kuji/lootbox, reviews, notifications, and audit entries are site-owned — every site-owned record, query, mutation, cache key, event, and realtime message MUST retain unambiguous site identity. Membership defines which users belong to which sites.

9. **Domain-modular monolith** (ADR-0001): `inventory-service` stays one Spring Boot deployment/process/transaction boundary — no split into networked services — but its code is organized into business-domain modules (`catalog`, `sites`, `identity`, `inventory`, `transfers`, `shipments`, `displays`, `kuji`, `lootbox`, `analytics`, `notifications`, `reviews`, `audit`, `shared`) with mechanically enforced dependency rules, migrating away from the old controllers/services/repositories/models layering.

## Code Style

- No emojis in code, comments, or documentation
- No `console.log` in production code
- No hardcoded secrets — environment variables only
- Functions under 50 lines, files 200–400 lines typical (800 max)
- Validate all user inputs with Zod (frontend) / Bean Validation (backend)
- Parameterized queries only — no string-concatenated SQL

## Testing

- TDD: write the failing test first, then implement
- 90% minimum coverage
- Unit tests for utilities, integration tests for APIs (Testcontainers), pytest/Docker Compose E2E coverage in `tests/e2e`, and Kafka event-envelope contract checks in `tests/contracts`

## Spec-Driven Development workflow

New work (after the pre-existing `refactor/multi-site` branch merges) follows the SDD workflow defined in `AGENTS.md` and `docs/sdd-workflow.md`:

- Classify each change as **Trivial**, **Standard**, or **Full** tier before editing. Full tier covers cross-service behavior, Kuji lifecycle, forecasting, RBAC/auth, public contracts, migrations, async delivery, or deployment changes.
- Standard/Full work gets a `.specs/<feature-id>/spec.md` (+ `log.md`, and for Full, `review.md` + `validation.md`).
- `docs/specs/`, `docs/adr/`, and `docs/plans/` are the durable architecture record — read the relevant ones before touching architecture, tenant data, authorization, public APIs, events, migrations, or deployment behavior.

## Available Commands

- `/spec` — classify SDD work and create the feature specification
- `/build` — implement one planned task with the relevant TDD path
- `/validate` — run applicable local checks and prepare PR-gate evidence
- `/review` — review Full-tier work before commit
- `/commit` — create a conventional commit (global command)
