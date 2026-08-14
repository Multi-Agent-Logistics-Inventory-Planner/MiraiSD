# Events and Replica Readiness Specification

- Status: Draft
- Date: 2026-08-11
- Scope: Spring, forecasting, messaging, Kafka and process-local coordination
- Roadmap: [Enterprise modernization](../roadmap/enterprise-modernization.md)

Sections 1–5 and the single-broker requirements in §8 are active reliability scope. Sections 6–7,
replica-specific tests in §9 and the scale-out gate are deferred until more than one relevant process
or host is planned. No Redis or replica infrastructure is required for initial modernization.

## 1. Delivery guarantee

MiraiSD integration events are delivered at least once. Exactly-once delivery is not claimed.
Publishers and consumers MUST tolerate retries and duplicates while preserving business effects.

## 2. Event envelope

Every versioned integration event contains:

- stable event ID and event type;
- explicit event version;
- occurred-at timestamp;
- site ID for site-owned events;
- entity ID and type;
- actor ID when human initiated;
- correlation ID;
- causation ID when caused by another event;
- idempotency key when associated with an idempotent command;
- version-specific payload.

Inventory events use a stable `site_id + product_id` partition key so related updates retain order
within a topic partition. Schemas live in `packages/contracts` and compatibility tests cover every
producer and consumer.

## 3. Transactional outbox creation

Business mutation and outbox insertion occur in the same PostgreSQL transaction. External Kafka
publication does not occur inside the business transaction. Unique event identity prevents creating
multiple logical events for one committed transition.

## 4. Publisher claims

Publishers atomically claim a bounded batch with `FOR UPDATE SKIP LOCKED` or an equivalent persisted
lease. Claims record owner and expiration, and abandoned claims can be recovered. Success records
publication time; failure records attempts and next-attempt time using bounded backoff.

A crash after Kafka accepts the event but before PostgreSQL records success can republish the event.
Consumer idempotency is therefore mandatory. Dead-letter movement is observable and recoverable; it
does not silently discard business history.

## 5. Consumer idempotency

Each consumer persists processed event IDs within the same transaction as its durable effect where
possible. Duplicate delivery produces no duplicate forecast update, notification, Slack message or
review state. Poison events enter a monitored dead-letter path with schema/version and error context.

Consumer tests include duplicate, reordering where possible, retry after partial failure and unknown
compatible fields/versions.

## 6. Scheduled and background work

The following requirements become active before the affected process is replicated:

- Spring HTTP APIs and scheduled workers run under separate profiles or commands.
- API replicas execute no cron jobs or outbox loops.
- Analytics, review and other singleton schedules use database-backed locks or dedicated singleton
  processes.
- Notification delivery and polling use atomic claims/leases.
- Jobs record last success, duration, failures and next scheduled execution.
- Messaging process-local scheduling is removed or made persisted and concurrency-safe.

## 7. Process-local state

Before adding replicas:

- JVM-local rate limiting moves to Redis or an equivalently shared store.
- Next.js server-route rate limits receive equivalent treatment if the web runtime is replicated.
- Correctness-critical caches are removed or externally coordinated.
- Performance caches use site-qualified keys and tolerate independent instances.
- Database connection-pool budgets are calculated per replica and use the Supabase pooler where
  appropriate.

Redis is a replica-readiness dependency, not a requirement for the initial single API process unless
current abuse protection requires globally consistent limits.

## 8. Kafka topology

The single-host phase retains one persistent broker with durable volume and monitored disk/lag. Kafka
is private to the application network. A two-broker cluster is prohibited. Higher availability means
managed Kafka or a proper three-node topology on independent failure domains.

## 9. Replica acceptance tests

- Two concurrent outbox publishers exclusively claim rows.
- Crash after publish produces a duplicate delivery but one consumer effect.
- Two scheduler workers produce one effective job execution.
- Two notification workers deliver once.
- Multiple API instances enforce a shared rate limit.
- Pool budgets remain below database capacity during replica load tests.
- Kafka partition tests retain order for one site/product key.
- Lag, oldest outbox age and dead-letter growth trigger alerts.

## 10. Scale-out gate

No second API replica or host is introduced until publisher claims, consumer idempotency, worker
separation, shared rate limits, connection budgets and concurrent acceptance tests are complete.
