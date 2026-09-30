# Devices API

A REST API for persisting and managing device resources: create, update (full/partial),
fetch (single/all/by brand/by state), and delete, with domain rules enforced around device
state.

> Status: project scaffolded (Maven build, dependencies, package layout). Domain model,
> persistence, and API implementation are next. This document will be filled in
> (run instructions, limitations) as the project progresses.

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
filterable, rather than duplicated endpoints.

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
| Coverage | JaCoCo with an enforced minimum threshold |

Design decisions and their trade-offs (ID strategy, error format, PATCH semantics,
layered vs hexagonal, etc.) are documented in [`docs/DECISIONS.md`](docs/DECISIONS.md).

## Testing strategy

1. Service unit tests (Mockito) — business rule enforcement.
2. Repository slice tests (`@DataJpaTest` + Testcontainers).
3. Web layer tests (`@WebMvcTest` + MockMvc).
4. Full integration tests (`@SpringBootTest` + Testcontainers, real HTTP).

## Running locally

_(To be documented once the project is scaffolded: `docker compose up`, local build/run,
where to find Swagger UI, how to run the test suite.)_

## Known limitations / future improvements

_(To be filled in as implementation proceeds.)_
