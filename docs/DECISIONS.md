# Architecture & Design Decisions

Format per entry: Context / Decision / Alternatives / Consequences.

## 1. Build tool: Maven

**Context** — The project needs a build tool and a pinned runtime.
**Decision** — Maven, with the Maven Wrapper (`mvnw`) committed so the build is
reproducible without a pre-installed Maven. Java 21 and Spring Boot 3.5.x, the current
supported release line.
**Alternatives** — Gradle: faster incremental builds and a more flexible DSL, at the cost
of build logic that is less explicit to read. Staying on an older Spring Boot line: fewer
moving parts now, but it drops out of open-source support and accumulates upgrade debt.
**Consequences** — Slightly more verbose configuration; in exchange, dependency and
plugin declarations are explicit, and the runtime stays on a supported, patched version.

## 2. Database: PostgreSQL + Flyway

**Context** — The application needs persistence on a non-in-memory database.
**Decision** — PostgreSQL, schema managed by versioned Flyway migrations
(`src/main/resources/db/migration`), with `spring.jpa.hibernate.ddl-auto=validate` so
Hibernate can never silently mutate the schema.
**Alternatives** — Hibernate `ddl-auto=update`: quicker to stand up, but the schema
becomes an implicit side effect of entity code, with no migration history and no review
point before a production change. MongoDB: viable, but a fixed five-field record filtered
by exact brand/state matches is relational-shaped and gains nothing from a document model.
**Consequences** — One extra moving part (migration files), in exchange for a
version-controlled, reviewable schema history.

The state column carries a `CHECK` constraint (migration `V4`) restricting it to the three
enum values. The application already guarantees this, but the column is a `VARCHAR`, so the
constraint keeps the invariant true for anything writing to the table outside the
application — a later migration, a manual correction, or another service.

## 3. Architecture: layered, with the domain rules on the entity

**Context** — A single aggregate (`Device`) with three business rules: creation time is
immutable, name and brand are frozen while the device is in use, and an in-use device
cannot be deleted.
**Decision** — Layered (`controller` → `service` → `repository`), with the rules enforced
inside the `Device` entity itself: `Device.update` rejects a name or brand change while
in use, and `Device.assertDeletable` rejects deletion. Creation time has no setter at all,
so its immutability is structural rather than checked. The service orchestrates loading
and saving and holds no rule logic.
**Alternatives** — Enforcing the rules in the service layer (the original approach): fine
while one service is the only caller, but the entity stays mutable through public setters,
so any later code path can change a frozen field without passing the check. Hexagonal /
ports-and-adapters: stronger isolation from the persistence framework, but the ports,
adapters, and mapping layers are disproportionate to a five-field CRUD domain.
**Consequences** — Rules cannot be bypassed by a future caller, and they are unit-testable
without Spring or mocks (`DeviceTest`). The cost is that the entity now depends on the
exception types it throws.

## 4. API documentation: springdoc-openapi

**Context** — The API needs documentation.
**Decision** — `springdoc-openapi-starter-webmvc-ui`, generating the OpenAPI 3 spec and
Swagger UI from annotated controllers and DTOs, with `@Operation`/`@ApiResponse`
describing each operation and its error statuses.
**Alternatives** — A hand-written OpenAPI document: full control over wording, but nothing
keeps it in sync with the code, so it silently drifts.
**Consequences** — Documentation is generated from the running code and cannot describe an
endpoint that no longer exists; the cost is annotation volume on the controller.

## 5. Resource ID strategy: application-generated UUID

**Context** — `id` needs a generation strategy.
**Decision** — UUID (v4), generated in the application before persistence.
**Alternatives** — A database identity/`BIGSERIAL` column: smaller index and simpler, but
sequential IDs are enumerable by clients, and the ID is only known after the insert.
**Consequences** — A larger key than a bigint, in exchange for non-enumerable IDs that are
known before the row is written. See decision 11 for how this interacts with `@Version`.

## 6. Error response format: `ProblemDetail` (RFC 9457)

**Context** — Validation failures, missing resources, and business-rule conflicts all need
a consistent, machine-readable error shape.
**Decision** — `ProblemDetail`, Spring 6's implementation of the problem-details format
(RFC 9457, which obsoletes RFC 7807), from a single `@RestControllerAdvice`. Malformed or
invalid input → 400; a valid request that conflicts with the resource's current state →
409; unknown resource → 404.
**Alternatives** — A bespoke error DTO: more control over the exact shape, but reimplements
a standard the framework already provides and that clients may already understand.
**Consequences** — Self-describing error bodies (`type`, `title`, `status`, `detail`,
`instance`) with no custom error model to maintain.

## 7. Update semantics: `PUT` replaces, `PATCH` merges

**Context** — The API supports both full and partial updates, so the two verbs need
distinct, defensible semantics.
**Decision** — `PUT` takes `DeviceRequest`, where every field is required: the body
must carry a complete representation, and an incomplete one is a 400. `PATCH` takes
`UpdateDeviceRequest`, where every field is optional and an omitted field means "leave
unchanged". No field is nullable in the domain, so "explicitly set to null" is never a
meaningful request and the two cases need not be distinguished.
**Alternatives** — Sharing one optional-field DTO across both verbs (the original
approach): less code, but it made `PUT` and `PATCH` behave identically — `PUT {}` returned
200 and changed nothing — so the full/partial distinction existed in name only. JSON Merge
Patch (RFC 7396) or JSON Patch (RFC 6902): standards-based, but both add content-type or
operation-document handling that four flat fields do not justify.
**Consequences** — Each verb means what the HTTP spec says it means. There are two request
DTOs rather than three: POST and PUT both take a complete device, so they share
`DeviceRequest`, and only the genuinely different shape (all-optional, for PATCH) gets its
own type. One DTO per request *shape*, not per endpoint.

## 8. Filtering: query parameters on the collection, not separate routes

**Context** — "Fetch by brand" and "fetch by state" are both required.
**Decision** — Both are served by `GET /api/v1/devices?brand=&state=`.
**Alternatives** — Dedicated `/devices/brand/{brand}` and `/devices/state/{state}` routes:
a literal reading of the requirement, but it duplicates controller and query logic for
what is one collection with two optional filters, and the two filters cannot be combined.
**Consequences** — One endpoint and one query path; the filters compose, so brand and
state can be applied together.

## 9. Test database: Testcontainers, not H2

**Context** — The tests need a database. The production database is PostgreSQL.
**Decision** — Testcontainers running real PostgreSQL for repository slice tests and full
integration tests, started once per JVM via the singleton-container pattern.
**Alternatives** — H2 in-memory: a faster suite, but it executes different SQL than
PostgreSQL, so Flyway migrations and any dialect-specific behaviour would be validated
against an engine the application never actually runs on.
**Consequences** — Slower startup, in exchange for tests that exercise the same database
engine and the same migrations as production.

## 10. Observability: Actuator, Prometheus, and structured operational defaults

**Context** — The service needs to be diagnosable and orchestratable once deployed.
**Decision** — `spring-boot-starter-actuator` with `micrometer-registry-prometheus`,
exposing `health`, `info`, `metrics`, and `prometheus`; liveness/readiness probes enabled;
`server.shutdown=graceful` so in-flight requests finish on SIGTERM. Application logs record
rule violations at WARN and unexpected failures at ERROR with the full stack trace.
**Alternatives** — Omitting them: fewer dependencies, but no health endpoint for the
container healthcheck, no scrapeable metrics, and in-flight requests dropped on redeploy.
**Consequences** — One extra dependency and a few configuration lines; the health endpoint
is consumed directly by the Docker `HEALTHCHECK`, so it is not speculative.

## 11. Concurrency: optimistic locking via `@Version`

**Context** — Updating or deleting a device is a read, then a rule check, then a write.
PostgreSQL's default `READ COMMITTED` isolation does not prevent the row changing in
between, so two concurrent requests can both read the same device and the second write can
silently discard the first. Concretely: both read an `AVAILABLE` device; A sets
`state=IN_USE` and saves; B, which only meant to change the name, still holds the stale
`AVAILABLE` value and writes it back, erasing A's change.
**Decision** — A `@Version Long version` column on `Device` (migration `V2`). Hibernate
conditions every `UPDATE` on the version it read and increments it, so the second writer
affects zero rows and Spring raises `ObjectOptimisticLockingFailureException`, mapped to
409 with a "re-fetch and retry" message.

The version field is a boxed `Long` rather than a primitive, which matters because the ID
is assigned by the application (decision 5). Spring Data decides whether `save()` should
insert or merge by asking whether the entity is new; with a boxed version, only a `null`
version means new, so a freshly created device goes straight to `persist` with no
preceding `SELECT`. With a primitive version, Spring Data falls back to inspecting the ID,
and since the ID is always populated it would treat every new device as pre-existing and
call `merge()` instead, issuing a `SELECT` before each `INSERT`. The cost is an extra
query per create, not incorrect data. `DeviceRepositoryTest.createIssuesNoSelectBeforeInsert`
asserts the no-`SELECT` behaviour directly.
**Alternatives** — Pessimistic locking (`SELECT ... FOR UPDATE`): correct, but it
serialises access to a row on every update and delete, which is the wrong trade when
concurrent edits to the same device are expected to be rare. A single conditional
`UPDATE ... WHERE state != 'IN_USE'`: closes the window without a version column, but does
not express the "only reject an actual value change" rule and abandons the
read-modify-save style used elsewhere.
**Consequences** — One column and one field; clients must handle a 409 on update/delete by
re-fetching. The conflict is reported with its own title so it is distinguishable from the
two business-rule conflicts. `version` is not exposed on the API, so this protects the
server's invariants but does not give clients `ETag`/`If-Match` conditional requests.

## 12. Containerization: multi-stage Dockerfile, Alpine runtime, tests outside the build

**Context** — The application must be containerized.
**Decision** — A two-stage `Dockerfile`: an `eclipse-temurin:21-jdk-jammy` build stage that
uses the committed Maven Wrapper, and an `eclipse-temurin:21-jre-alpine` runtime stage
containing only the packaged jar, running as a non-root user with a `HEALTHCHECK` against
`/actuator/health` (using Alpine's BusyBox `wget`, so no extra package is needed). The
build stage skips tests: the repository and integration tests need a Docker socket that
the image build does not have, so test execution belongs to `./mvnw verify` in CI or local
development.
**Alternatives** — A single-stage build: a simpler Dockerfile, but it ships the JDK and
Maven's dependency cache in the runtime image. A generic `maven:*` build image: also works,
but pins a different Maven version than the wrapper used everywhere else.
**Consequences** — A small runtime image with no build toolchain in it. Verified by running
the stack with `docker compose up --build` and exercising the API over HTTP, rather than
only in tests.

## 13. Pagination and default ordering on the collection endpoint

**Context** — `GET /api/v1/devices` originally returned every device in one unpaginated,
unordered response.
**Decision** — The repository, service, and controller take a `Pageable` and return a
`Page`; the controller binds the standard `page`/`size`/`sort` parameters via
`@PageableDefault(size = 20, sort = "creationTime", direction = DESC)`.
`@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)` is enabled so the response
shape is a documented contract rather than a serialized `PageImpl`.
**Alternatives** — No default sort: PostgreSQL does not guarantee row order without an
`ORDER BY`, so the same row can appear on two pages or on none while paging. Returning
`Page` without the DTO serialization mode: Spring Data explicitly warns that the resulting
JSON is not a stable contract.
**Consequences** — The collection response is `{"content": [...], "page": {...}}` and
newest-first by default. Any entity field is sortable; there is no curated allow-list.

## 14. Composite index on `(brand, state)`

**Context** — `findByBrandAndState` filters on both columns, which were previously served
by two separate single-column indexes.
**Decision** — A composite index on `(brand, state)`, dropping the now-redundant
single-column `brand` index, since a composite btree index also serves lookups on its
leading column. The single-column `state` index stays, because `state` is the trailing
column here and is not served by this index alone.
**Consequences** — One index instead of two for brand-related queries, so one less index to
maintain on every write; the state-only path is unchanged.

## 15. JaCoCo: report only, no enforced coverage threshold

**Context** — The project needs demonstrable test coverage.
**Decision** — `jacoco-maven-plugin` (`prepare-agent` + `report`) so every `./mvnw verify`
produces a coverage report at `target/site/jacoco/index.html`, without a `check` execution
that fails the build below a threshold.
**Alternatives** — An enforced minimum: automated protection against regression, but the
threshold value is arbitrary, and a hard gate can block an unrelated change because of
coverage in a file it did not touch.
**Consequences** — Coverage is visible on every build but not enforced, so preventing
regression is a review responsibility. One wiring note: the surefire `argLine` must be
`@{argLine}` rather than a literal string, otherwise JaCoCo's injected `-javaagent` flag is
silently dropped and the report is empty.

## 16. Error handling: extend `ResponseEntityExceptionHandler`

**Context** — The advice had a catch-all `@ExceptionHandler(Exception.class)` so unexpected
failures would be logged rather than disappear. It also intercepted the exceptions Spring
MVC raises for malformed client requests, which already carry correct 4xx statuses. As a
result an unparseable UUID in the path, an unknown enum value in a query parameter or body,
malformed JSON, and an unknown `sort` property all returned 500 — reporting a server fault
for a client mistake.
**Decision** — `GlobalExceptionHandler` extends `ResponseEntityExceptionHandler`, so
Spring's typed request exceptions keep their intended statuses, plus an explicit handler
mapping Spring Data's `PropertyReferenceException` to 400. The catch-all remains as a last
resort; more specific handlers take precedence. `handleMethodArgumentNotValid` is overridden
rather than re-declared (two `@ExceptionHandler` methods for one type is an ambiguous
mapping) so that every field error is reported in one response.

The advice is scoped to `com.deviceapi.web` rather than applying globally. An unscoped
advice also intercepts Actuator's endpoints and Spring MVC's own infrastructure, which
turned a legitimate 404 for an unmatched route into a 500.
**Alternatives** — Declaring an `@ExceptionHandler` per Spring exception type:
equivalent behaviour, but it duplicates a list the framework already maintains and will
silently miss types added in future versions.
**Consequences** — Client errors are reported as 4xx with a `ProblemDetail` body, and
genuine server faults are still logged with a stack trace and returned as a generic 500
that leaks no internals. Covered at both the `@WebMvcTest` and full-integration levels.

## 17. Rejecting unknown JSON fields

**Context** — Jackson ignores unknown properties by default, so a request such as
`PATCH {"creationTime": "..."}` returned 200 while doing nothing. The immutability rule
held, but the client was told its request succeeded.
**Decision** — `spring.jackson.deserialization.fail-on-unknown-properties: true`, which
makes such a request a 400 via the error handling in decision 16.
**Alternatives** — Leaving the default: more lenient for clients sending extra fields, but
it silently discards input, which is indistinguishable from the server accepting it.
**Consequences** — Clients must send only fields the DTO declares; typos and attempts to
set server-controlled fields are reported rather than ignored.
