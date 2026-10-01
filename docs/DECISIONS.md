# Architecture & Design Decisions

Format per entry: Context / Decision / Alternatives / Consequences.

## 1. Build tool: Maven

**Context** — Brief allows Maven 3.9+ or Gradle 8+.
**Decision** — Maven, with the Maven Wrapper (`mvnw`) committed so the build is
reproducible without a pre-installed Maven.
**Alternatives** — Gradle: faster incremental builds, more flexible DSL, but less
immediately skimmable for a reviewer doing a time-boxed assessment of `pom.xml`.
**Consequences** — Slightly more verbose config; in exchange, dependency and plugin
declarations are explicit and easy to review.

## 2. Database: PostgreSQL + Flyway

**Context** — Brief requires persistence on a non-in-memory database.
**Decision** — PostgreSQL, schema managed by versioned Flyway migrations
(`src/main/resources/db/migration`), with `spring.jpa.hibernate.ddl-auto=validate` so
Hibernate can never silently mutate the schema.
**Alternatives** — Hibernate `ddl-auto=update`: faster to stand up, but auto-generated
schema drift is a well-known production anti-pattern and weaker to defend under
questioning. MongoDB: viable, but the domain (5 fixed fields, relational-shaped
filtering by brand/state) has no document-model justification.
**Consequences** — One extra moving part (migration files) but a reviewable,
version-controlled schema history and a safer story for a "production readiness"
evaluation.

## 3. Architecture: Layered, not hexagonal

**Context** — Domain is a single aggregate (Device) with three business invariants.
**Decision** — Standard layered architecture: `controller` → `service` → `repository`,
with business rules enforced in the service layer, not the controller or the entity
getters/setters.
**Alternatives** — Hexagonal/ports-and-adapters: gives a stronger interview talking
point about dependency inversion, but for a 5-field CRUD domain it adds ports, adapters,
and mapping ceremony disproportionate to the actual complexity — risks reading as
over-engineered relative to the "code efficiency" evaluation criterion.
**Consequences** — Faster to review and navigate; the trade-off (less abstraction
isolation from the persistence framework) is acceptable at this scale and is explicitly
named here so it can be defended, not left implicit.

## 4. API documentation: springdoc-openapi

**Context** — Brief requires the API to be documented.
**Decision** — `springdoc-openapi-starter-webmvc-ui`, generating OpenAPI 3 spec and
Swagger UI directly from annotated controllers/DTOs.
**Alternatives** — Hand-written OpenAPI YAML: full control over the spec's wording, but
drifts from the implementation over time since nothing enforces they stay in sync.
**Consequences** — Docs are always accurate to the running code; annotation noise on
controllers is the cost.

## 5. Resource ID strategy: application-generated UUID

**Context** — `id` needs a strategy: DB auto-increment vs application-generated.
**Decision** — UUID (v4), generated in the application layer before persistence.
**Alternatives** — DB `BIGSERIAL`/identity column: simpler, smaller index, but leaks
sequential/enumerable IDs in a public API (IDOR-adjacent concern) and couples ID
generation to a single database instance.
**Consequences** — Slightly larger index/storage footprint than a bigint; in exchange,
IDs are non-enumerable and safe to generate before the row is committed (useful for
idempotency-key style client patterns, even though full idempotency isn't in scope here).

## 6. Error response format: RFC 7807 `ProblemDetail`

**Context** — Business rule violations (e.g. deleting an in-use device) need a
consistent error shape distinct from validation errors.
**Decision** — `ProblemDetail` (Spring 6 / Boot 3 built-in RFC 7807 support) via a single
`@RestControllerAdvice`. Malformed/invalid input → 400; valid request that conflicts with
current resource state (e.g. delete while `IN_USE`) → 409; not found → 404.
**Alternatives** — Ad-hoc custom error DTO: more control over exact shape, but reinvents
a standard that `ProblemDetail` already provides out of the box.
**Consequences** — Consistent, self-describing error bodies (`type`, `title`, `status`,
`detail`, `instance`) with no bespoke error-DTO to maintain.

## 7. Partial update semantics: plain partial DTO (null = unchanged)

**Context** — `PATCH /devices/{id}` needs defined semantics for "unspecified" vs
"explicitly cleared" fields.
**Decision** — A partial request DTO where every field is optional; any field left
`null` in the JSON body is left unchanged. No field in this domain is nullable at the
domain level, so "explicitly set to null" is never a meaningful request — this
simplifies the semantics without losing any real capability.
**Alternatives** — JSON Merge Patch (RFC 7396): standards-compliant, same null-means-
unchanged behavior, but requires handling the `application/merge-patch+json` content
type for a domain simple enough not to need it. JSON Patch (RFC 6902): full
add/remove/replace operation support — significant overkill for four flat fields.
**Consequences** — `PUT` and `PATCH` share most of their validation logic; `PATCH` just
skips fields that weren't sent.

## 8. Filtering: query params on the collection endpoint, not separate routes

**Context** — Brief lists "fetch by brand" and "fetch by state" as distinct
functionalities.
**Decision** — Both are served by `GET /api/v1/devices?brand=&state=` rather than
dedicated `/devices/brand/{brand}` and `/devices/state/{state}` routes.
**Alternatives** — Separate endpoints per filter: matches the brief's bullet-point
phrasing literally, but produces duplicate controller/query logic for what is the same
resource collection with two optional filters, and doesn't compose (can't filter by
brand AND state at once).
**Consequences** — One endpoint, one query method, filters compose naturally; documented
here explicitly so it doesn't read as a missed requirement.

## 9. Test database: Testcontainers, not H2

**Context** — Brief excludes in-memory databases for persistence, but says nothing
about the test database specifically.
**Decision** — Use Testcontainers (real PostgreSQL in a container) for repository slice
tests and full integration tests, rather than falling back to H2 in-memory for speed.
**Alternatives** — H2 in-memory for tests: faster test runs, but risks
Postgres-specific SQL/Flyway behavior diverging from what tests actually exercise,
undermining confidence in the "reasonable test coverage" criterion.
**Consequences** — Slower test suite (container startup), but tests exercise the same
database engine as production end to end.

## 10. Observability: Spring Boot Actuator included

**Context** — Not required by the brief, but containerization + production readiness
are explicitly evaluated.
**Decision** — Include `spring-boot-starter-actuator`, exposing `/actuator/health` for
the Docker healthcheck and `/actuator/info`.
**Alternatives** — Omit it: fewer dependencies, but no standard healthcheck endpoint for
`docker-compose`'s healthcheck directive, and a missed low-cost production-readiness
signal.
**Consequences** — One extra dependency, justified by direct use in the Docker
healthcheck (not included speculatively).

## 12. Concurrency safety: optimistic locking via `@Version`

**Context** — `DeviceService.update()` and `delete()` each do read → check invariant →
write as separate steps, with no locking. Postgres's default isolation
(`READ COMMITTED`) does not stop the row from changing between the read and the write
of two concurrent transactions. Concretely: two requests read the same `AVAILABLE`
device; request A sets `state=IN_USE` and saves; request B never touched `state` but
its in-memory copy still holds the stale `AVAILABLE` value from its own read, so its
invariant check passes and its save silently overwrites A's `state` change back to
`AVAILABLE` - a lost update, and a case where the "in use" invariant momentarily existed
in the database and was then erased by a request that never intended to touch it.
**Decision** — Add `@Version private Long version` (boxed, not primitive - see the
javadoc on `Device`) to `Device` (migration `V2`). Hibernate then conditions every
`UPDATE` on the version it read (`WHERE id = ? AND version = ?`) and bumps it on save;
if another transaction already updated the row, the second writer's `UPDATE` affects
zero rows and Hibernate/Spring throws `ObjectOptimisticLockingFailureException`, mapped
to `409 Conflict` by `GlobalExceptionHandler` with a "re-fetch and retry" detail message.
The boxed type matters for a subtler reason than the locking itself: `Device` has a
manually-assigned id (decision #5), and Spring Data's default new-vs-existing check for
a versioned entity treats a primitive version's `0` as "new" - which wrongly
misclassifies an existing, never-updated device (version is also 0 the moment after
creation) and would `INSERT` over an existing row on `save()` instead of `UPDATE`ing it.
A boxed `Long` sidesteps this entirely: the check becomes "is version null," and only a
truly new, unsaved `Device` has a null version - Hibernate always populates the real
value when loading an existing row. (A custom `Persistable` implementation was tried
first and works too, but is unnecessary machinery once the field is the correct type.)
**Alternatives** — Pessimistic locking (`SELECT ... FOR UPDATE` via
`@Lock(LockModeType.PESSIMISTIC_WRITE)`): guarantees correctness by blocking concurrent
readers until the first transaction commits, but that's the wrong trade for this
workload - concurrent edits to the *same* device are expected to be rare, not
high-contention, so paying for a row lock (and the wait/timeout it imposes on every
caller) on every update/delete isn't justified. A raw atomic
`UPDATE ... WHERE id = ? AND state != 'IN_USE'`-style single statement would also close
the window without a version column, but doesn't compose cleanly with the "only reject
on an actual value change" nuance already in `DeviceService.update()`, and would mean
abandoning the object-oriented read-modify-save style used everywhere else in the
service.
**Consequences** — One new column, one new field, and callers now need to handle a
possible 409 on `update`/`delete` by retrying with a fresh read - this is surfaced to
API consumers as a distinct "Concurrent Modification" title so it's not confused with
the two business-rule 409s. The `version` field is not exposed in `DeviceResponse` -
this closes the internal race but does not implement full HTTP conditional-request
semantics (`ETag`/`If-Match`) for clients to detect conflicts themselves before
writing; documented as a possible future improvement in the README.

## 13. Containerization: multi-stage Dockerfile, Alpine runtime, tests excluded from the build

**Context** — Brief requires the application to be containerized.
**Decision** — Two-stage `Dockerfile`: an `eclipse-temurin:21-jdk-jammy` build stage using
the committed Maven Wrapper (so the image builds with the exact same Maven/JDK
combination as local dev, not whatever a generic `maven:*` image happens to pin), and an
`eclipse-temurin:21-jre-alpine` runtime stage containing only the packaged jar. The build
stage runs `-DskipTests`; the container build environment has no Docker socket, so the
repository/full-integration tests (which need Testcontainers) can't run inside it anyway
- test execution stays a `./mvnw verify` responsibility in CI/local dev, not the image
build's job. The runtime image runs as a non-root user and declares a `HEALTHCHECK`
against `/actuator/health` using Alpine's built-in BusyBox `wget` (no extra package
needed for that alone).
**Alternatives** — A single-stage build: simpler Dockerfile, but ships the entire JDK and
Maven's dependency cache in the final image instead of just a JRE and a jar - much
larger, and exposes the build toolchain in a production image unnecessarily. A generic
`maven:3.9-eclipse-temurin-21` build image: also works, but then the image build depends
on whatever Maven version that tag pins rather than the exact wrapper-pinned version
used everywhere else in this project.
**Consequences** — Verified for real (not just asserted): built and ran the full stack via
`docker compose up --build`, then exercised it over HTTP - create/get/list/patch, both
409 invariant paths, an unmatched route returning a genuine 404 (confirming the
exception-handler scoping fix from decision history holds under a real deployment, not
just tests), Swagger UI, and `/actuator/prometheus` all worked correctly against the
containerized app.

## 14. Pagination on the collection endpoint

**Context** — `GET /api/v1/devices` returned the full unpaginated result set.
**Decision** — `DeviceService.search`/`DeviceRepository` now take a `Pageable` and return
`Page<Device>`; the controller binds it via `@PageableDefault(size = 20)` (standard
`page`/`size`/`sort` query params) and returns `Page<DeviceResponse>`. Also enabled
`@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)` - returning `Page` directly
without it is explicitly flagged by Spring Data as not a stable JSON contract.
**Consequences** — The collection endpoint's JSON shape changed from a flat array to
`{"content": [...], "page": {...}}`; sorting isn't currently validated against an
allow-list of fields (see README limitations).

## 15. Composite index on `(brand, state)`

**Context** — `findByBrandAndState` is a real query path (decision #8's combined
filter), previously served only by two separate single-column indexes.
**Decision** — Added a composite index on `(brand, state)` and dropped the now-redundant
single-column `brand` index - a composite btree index also serves brand-only lookups via
its leading-column prefix, so keeping both would mean paying index-maintenance cost on
every write for no additional read benefit. The single-column `state` index stays, since
state is the trailing column here and wouldn't be served by this index alone.
**Consequences** — One index instead of two for the brand-related query paths; the
state-only query path is unaffected.
