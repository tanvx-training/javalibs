package io.javalibs.authz.jpa;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Minimal Boot app so the module's entities/repositories can be integration-tested. */
@SpringBootApplication
@EntityScan(basePackageClasses = AuthzUserEntity.class)
@EnableJpaRepositories(basePackageClasses = AuthzUserRepository.class)
public class AuthzJpaTestApplication {
}
