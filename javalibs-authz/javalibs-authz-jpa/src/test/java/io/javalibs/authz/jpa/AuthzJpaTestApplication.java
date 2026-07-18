package io.javalibs.authz.jpa;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Boot app so the module's entities/repositories can be integration-tested.
 *
 * <p>Entity scanning and {@code @EnableJpaRepositories} are intentionally <em>not</em>
 * declared here: since Task 18 added the {@code javalibs-authz-spring-boot-starter} test
 * dependency, {@code AuthzJpaAutoConfiguration} (which carries its own
 * {@code @EntityScan}/{@code @EnableJpaRepositories} for the same base packages) is always
 * on this module's test classpath and auto-configures itself. Declaring both here and there
 * registers every {@code authz_*} repository bean twice and fails context startup with a
 * {@code BeanDefinitionOverrideException}.</p>
 */
@SpringBootApplication
public class AuthzJpaTestApplication {
}
