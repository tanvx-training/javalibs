package io.javalibs.test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for full-context integration tests.
 *
 * <p>Boots the application on a random port with the {@code test} profile active
 * and an isolated PostgreSQL database provided by Testcontainers (see
 * {@link PostgresContainerSupport}).
 *
 * <pre>{@code
 * class OrderApiIT extends BaseIntegrationTest {
 *
 *     @Autowired
 *     TestRestTemplate rest;
 *
 *     @Test
 *     void createsOrder() { ... }
 * }
 * }</pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class BaseIntegrationTest extends PostgresContainerSupport {
}
