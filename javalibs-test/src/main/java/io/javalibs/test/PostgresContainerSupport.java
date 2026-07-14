package io.javalibs.test;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Provides a single shared PostgreSQL Testcontainer for the whole test JVM and
 * wires its connection settings into the Spring context.
 *
 * <p>The container follows the singleton-container pattern: it starts once on
 * first use, is shared by every test class extending this support class, and is
 * cleaned up by Testcontainers' Ryuk resource reaper when the JVM exits. This
 * keeps integration test suites fast while giving each build a fully isolated
 * database.
 *
 * <p>Requires a running Docker daemon.
 */
public abstract class PostgresContainerSupport {

    public static final String IMAGE = "postgres:16-alpine";

    private static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>(IMAGE)
                .withDatabaseName("it_db")
                .withUsername("it_user")
                .withPassword("it_pass");
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /** Direct access to the shared container (e.g. to run SQL fixtures). */
    protected static PostgreSQLContainer<?> postgres() {
        return POSTGRES;
    }
}
