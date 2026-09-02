-- stock_movements.actor_id has no FK (dropped in V48 so users can be deleted), so once a
-- user row is gone the movement's actor is an unresolvable UUID. audit_logs already avoids
-- this by denormalizing actor_name at write time; stock_movements never got the same column.
--
-- Backfill mirrors V1's approach for audit_logs: resolve every existing row against the users
-- still present. Rows whose actor was already deleted stay NULL - that name is unrecoverable.
ALTER TABLE stock_movements ADD COLUMN actor_name VARCHAR(255);

UPDATE stock_movements sm
SET actor_name = u.full_name
FROM users u
WHERE sm.actor_id = u.id
  AND sm.actor_name IS NULL;
