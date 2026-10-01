# Architecture & Design Decisions

Short summary of each decision made on this project. Numbering is stable — code comments
reference these by number.

1. **Build tool: Maven.** Maven Wrapper committed, Java 21, Spring Boot 3.5.x (current
   supported line) — reproducible build, no upgrade debt.
2. **Database: PostgreSQL + Flyway.** Versioned migrations, `ddl-auto=validate` so
   Hibernate never mutates the schema; a `CHECK` constraint on `state` guards the column
   at the database level too, not just in application code.
3. **Architecture: layered, with the domain rules on the entity.** `Device` itself
   enforces the three business rules (not the service), so no future caller can bypass
   them by mutating a device another way.
4. **API documentation: springdoc-openapi.** Generated from annotated controllers/DTOs,
   so the docs can't drift out of sync with the code.
5. **Resource ID: application-generated UUID.** Non-enumerable, known before the row is
   written — see decision 11 for how this interacts with `@Version`.
6. **Error format: `ProblemDetail` (RFC 9457).** One consistent, machine-readable error
   shape for validation failures, missing resources, and conflicts.
7. **Update semantics: `PUT` replaces, `PATCH` merges.** `PUT` requires every field (true
   replacement); `PATCH` treats an omitted field as unchanged. Two DTOs, one per request
   shape, not per endpoint.
8. **Filtering: query parameters, not separate routes.** `brand`/`state` are optional
   query params on one collection endpoint, so they compose instead of needing dedicated
   routes.
9. **Test database: Testcontainers, not H2.** Real PostgreSQL in tests (one container per
   JVM) so migrations and SQL behaviour match production.
10. **Observability: Actuator, Prometheus, graceful shutdown.** Health/metrics endpoints
    feed the Docker healthcheck directly; not speculative additions.
11. **Concurrency: optimistic locking via `@Version`.** Prevents one request's write from
    silently overwriting another's on a concurrent update. The version field is a boxed
    `Long`, not a primitive, so Spring Data correctly treats a brand-new device as new.
12. **Containerization: multi-stage Dockerfile.** JDK build stage + Alpine JRE runtime,
    non-root, healthcheck; tests excluded from the image build (no Docker socket there).
13. **Pagination with a default sort.** Collection endpoint is paginated, newest-first by
    default — without a default order, Postgres doesn't guarantee stable paging.
14. **Composite index on `(brand, state)`.** One index replaces two single-column indexes
    for the combined-filter query path.
15. **JaCoCo: report only.** Coverage is visible on every build; no enforced threshold
    (an arbitrary number isn't worth the false positives it would block on).
16. **Error handling extends `ResponseEntityExceptionHandler`.** Keeps Spring's own 4xx
    exceptions (bad UUID, malformed JSON, unknown sort field) from being swallowed by the
    catch-all and reported as 500.
17. **Reject unknown JSON fields.** `fail-on-unknown-properties: true` so a client trying
    to set a field like `creationTime` gets a 400 instead of a silently-ignored 200.
