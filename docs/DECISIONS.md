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
