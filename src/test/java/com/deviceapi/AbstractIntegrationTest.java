package com.deviceapi;

import org.junit.jupiter.api.Tag;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared real-Postgres container for any test that needs a database
 * (repository slice tests, full integration tests).
 *
 * <p>Deliberately uses the singleton-container pattern - a static
 * initializer, no {@code @Testcontainers}/{@code @Container} - so the
 * container genuinely lives for the whole JVM. With the JUnit extension,
 * the container is stopped after each test class, which both wastes
 * startup time and would leave a cached Spring context pointing at a
 * dead database if a later class reused it. Ryuk removes the container
 * when the JVM exits.
 */
@Tag("integration")
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("devices")
                    .withUsername("devices")
                    .withPassword("devices");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
