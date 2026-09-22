# Review

## Pending implementation review

- Verify authorization behavior changes only for the three previously unguarded legacy stock-movement reads.
- Verify the base has no Kafka container or Kafka property.
- Verify system-admin proof checks that no membership is created.

## Implementation review

- The new base only provides PostgreSQL datasource properties; it starts no Kafka container.
- The Postgres class originally did not alter endpoint policy; review follow-up changed only the
  three unguarded stock-movement reads. Its forged-actor assertion verifies
  the persisted movement is attributed to the authenticated backend user, not the body value.
- The explicit system-admin case checks both the successful legacy request and absence of a newly
  created membership row.
- Review follow-up corrected the three legacy stock-movement reads, not merely the originally
  observed history route. Existing Q-6c-5 query primitives preserve null-site pre-backfill rows
  while excluding rows belonging to a non-MAIN site.
