package io.javalibs.search.spring;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

/**
 * Minimal test-only Spring Boot configuration so that {@code @DataJpaTest} can locate a
 * {@code @SpringBootConfiguration} and scan the test entities and repositories.
 */
@SpringBootApplication
@EntityScan(basePackageClasses = SearchTestApplication.class)
class SearchTestApplication {
}
