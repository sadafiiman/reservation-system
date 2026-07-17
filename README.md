# Reservation System

High-concurrency slot reservation backend built for the backend developer assignment.

## Architecture in one paragraph

MySQL is the source of truth. A Redis `ZSET` (`available_slots_zset`, scored by
`start_time` epoch seconds) mirrors a rolling window (default 7 days) of free slots and
acts as a fast, atomic queue: `ZPOPMIN` hands each concurrent request a different
candidate slot id in O(log N), so requests don't contend on the same DB rows. The winning
id is then confirmed inside a real MySQL transaction with a pessimistic row lock as a
safety net. A scheduled job (every 30s) reconciles Redis against MySQL so the system
self-heals if a request dies between the Redis pop and the DB commit. If Redis is
unavailable or its window is momentarily empty, requests fall back to a pure-MySQL path
using `SELECT ... FOR UPDATE SKIP LOCKED`, which still guarantees no double-booking and no
thread queuing behind another's lock.

See the full design write-up and trade-off discussion in `docs/DESIGN.md`.

## Run locally with Docker

```bash
cd deployment/local
cp .env.example .env
docker compose up --build
```

App will be available at `http://localhost:8080`.
Swagger UI: `http://localhost:8080/swagger-ui.html`

## Run locally without Docker (Mysql + local Redis)

```bash
redis-server &
mvn spring-boot:run
```

Default profile uses mysql database seeded via Flyway with the mock data from
the assignment doc, so it needs zero setup.

## API

| Method | Path                     | Auth | Description                          |
|--------|--------------------------|------|---------------------------------------|
| POST   | `/api/auth/register`     | none | Register a user, returns a JWT        |
| POST   | `/api/auth/login`        | none | Login, returns a JWT                  |
| POST   | `/api/reservations`      | JWT  | Reserve the nearest available slot    |
| DELETE | `/api/reservations/{id}` | JWT  | Cancel a reservation you own          |

Send `Authorization: Bearer <token>` on the two reservation endpoints.

## Tests

```bash
mvn test
```

```bash
mvn verify
```

`ConcurrentReservationIT` fires 100 concurrent reservation requests at 20 available slots
and asserts exactly 20 succeed, with zero slots double-booked — the core correctness
requirement of the assignment.

## Notes

- The mock `available_slots` rows from the assignment doc use dates in Dec 2024, which
  are in the past relative to when this is run; add your own future-dated rows (or adjust
  `V3__seed_mock_data.sql`) if you want to exercise the reservation endpoint against the
  seed data directly.
- JWT is required per the assignment's technical requirements; the "if you have time"
  clause was implemented in full including registration.
