# Design Notes & Trade-offs

## Why a MySQL-only solution wasn't enough

A naive `UPDATE available_slots SET is_reserved = TRUE WHERE id = ? AND is_reserved = FALSE`
works correctly but forces every concurrent request to either contend on the same "first
available" row (if everyone reads the same candidate) or requires a retry loop with extra
round trips as contention rises. Under high TPS this shows up directly in p99 latency.

## Why Redis + MySQL

Redis's `ZPOPMIN` on a `ZSET` is O(log N) and atomic: 100 concurrent callers each get a
*different* slot id back in a single round trip, with no lock contention between them at
all. MySQL is still authoritative -- Redis never invents availability, it only mirrors
what MySQL already confirmed as free. This keeps the "who gets the slot" decision fast
while keeping "is this slot really free" durable and consistent.

## Why not put all rows in Redis

The assignment specifies 1M+ rows per table. Mirroring all of them would waste memory on
slots users can never reasonably reach ("nearest available" only ever needs the near
future) and would make the reconciliation job O(table size) instead of O(window size). A
rolling window (configurable, default 7 days) bounds both.

## Why MySQL can't express "unique per active reservation" directly

MySQL has no partial/filtered unique index (unlike Postgres's
`CREATE UNIQUE INDEX ... WHERE status = 'CONFIRMED'`). The `active_slot_id` generated
column works around this: it evaluates to `slot_id` only while `status = 'CONFIRMED'` and
to `NULL` otherwise, and MySQL's unique index treats multiple `NULL`s as non-conflicting.
So the constraint effectively reads "at most one CONFIRMED reservation per slot" while
still allowing a slot to be re-booked after a cancellation.

## Consistency model between Redis and MySQL

Redis is a **fast index**, MySQL is the **source of truth**. The failure mode to worry
about is a crash between `ZPOPMIN` and the DB commit, which would make one slot briefly
invisible to new requests even though it's still free in MySQL. This is self-healed by
`SlotCacheSyncJob` within its next run (30s default) by re-`ZADD`-ing anything free in the
window — an idempotent operation. Worst case: a slot is briefly unbookable, never
double-booked.

## What I'd improve with more time

- Move the DB fallback's `FOR UPDATE SKIP LOCKED` query behind a circuit breaker so a
  Redis outage doesn't silently push 100% of traffic onto MySQL row locks at once.
- Partition/shard `available_slots` and `reservations` by date range once the 1M-row
  scale is realized in practice, rather than relying on indexing alone.
- Add a read-through cache (Spring Cache + Redis) for a `GET /api/reservations/{id}`
  endpoint, since the assignment focuses on write-path concurrency but a real system
  would also need fast reads.
- Add idempotency keys on `POST /api/reservations` so client-side retries after a network
  timeout can't accidentally create two reservations for the same logical request.
