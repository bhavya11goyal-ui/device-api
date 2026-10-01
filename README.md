# Devices API

A REST API for persisting and managing device resources: create, update (full/partial),
fetch (single/all/by brand/by state), and delete, with domain rules enforced around device
state.

> Status: feature-complete against the brief - CRUD, filtering, all three business
> invariants, concurrency safety, tests, and containerization are all in place and
> verified end-to-end. See Known Limitations below for what's intentionally left out.

## Domain model

**Device**
- `id` — UUID, generated application-side.
- `name` — String, required.
- `brand` — String, required.
- `state` — enum: `AVAILABLE`, `IN_USE`, `INACTIVE`.
- `creationTime` — Instant, set once at creation, server-controlled.

**Business rules**
1. `creationTime` is immutable after creation.
2. `name` and `brand` cannot be changed while `state == IN_USE`.
3. A device cannot be deleted while `state == IN_USE`.

## API

```
POST   /api/v1/devices                → create
GET    /api/v1/devices/{id}           → fetch one
GET    /api/v1/devices?brand=&state=  → fetch all / filter by brand / filter by state
PUT    /api/v1/devices/{id}           → full update (subject to business rules above)
PATCH  /api/v1/devices/{id}           → partial update
DELETE /api/v1/devices/{id}           → delete
```

`brand` and `state` are collapsed into query parameters on the collection endpoint rather
than separate routes (`/devices/brand/{brand}`, `/devices/state/{state}`) — one resource,
filterable, rather than duplicated endpoints. The collection endpoint is paginated
(standard Spring Data `page`/`size`/`sort` query params, default page size 20).

## Tech stack

| Concern | Choice |
|---|---|
| Language/runtime | Java 21, Spring Boot 3.3+ |
| Build tool | Maven |
| Database | PostgreSQL |
| Schema management | Flyway migrations |
| Architecture | Layered (Controller → Service → Repository), business rules enforced in the service layer |
| API docs | springdoc-openapi (Swagger UI) |
| Error format | RFC 7807 `ProblemDetail` |
| Test DB | Testcontainers (real Postgres, no in-memory DB even in tests) |
| Containerization | Multi-stage Dockerfile + docker-compose (app + Postgres) |

Design decisions and their trade-offs (ID strategy, error format, PATCH semantics,
layered vs hexagonal, etc.) are documented in [`docs/DECISIONS.md`](docs/DECISIONS.md).

## Testing strategy

1. Service unit tests (Mockito) — business rule enforcement.
2. Repository slice tests (`@DataJpaTest` + Testcontainers).
3. Web layer tests (`@WebMvcTest` + MockMvc).
4. Full integration tests (`@SpringBootTest` + Testcontainers, real HTTP).

## Running locally

**Via Docker Compose (app + Postgres, no local Java/Maven needed):**

```bash
docker compose up --build
```

Once healthy:
- API: `http://localhost:8080/api/v1/devices`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/actuator/health`
- Prometheus metrics: `http://localhost:8080/actuator/prometheus`

Stop and remove everything (including the Postgres volume) with `docker compose down -v`.

**Locally without Docker (needs a running Postgres on `localhost:5432` with a `devices`
database/user/password, or override via the `DB_*` env vars in `application.yml`):**

```bash
./mvnw spring-boot:run
```

**Running the test suite** (needs Docker running - repository and full integration tests
use Testcontainers):

```bash
./mvnw verify
```

## Known limitations / future improvements

- **No request correlation ID.** Considered and deliberately not built - genuinely useful
  in a multi-service/distributed context, but low value for a single-instance API of this
  size relative to the effort. See `docs/DECISIONS.md` discussion history.
- **No HTTP conditional-request support (`ETag`/`If-Match`).** Optimistic locking
  (`docs/DECISIONS.md` #12) prevents lost updates server-side, but clients can't detect a
  conflict themselves before writing via standard HTTP conditional headers - they only
  find out via the `409 Concurrent Modification` response.
- **No enforced coverage threshold.** The brief asks for "reasonable" coverage, not a
  numeric gate, and 41 tests across four distinct layers already demonstrate that
  qualitatively; a JaCoCo threshold would be a nice-to-have CI gate on top of coverage
  that already exists, not something currently missing.
- **No sorting control exposed.** `GET /api/v1/devices` accepts `page`/`size` (Spring
  Data's standard pagination params), and `sort` works since it's part of the same
  `Pageable` binding, but it isn't documented or validated against an allow-list of
  sortable fields - a client could request `sort=someInvalidField` and get a 500 rather
  than a clean 400.
