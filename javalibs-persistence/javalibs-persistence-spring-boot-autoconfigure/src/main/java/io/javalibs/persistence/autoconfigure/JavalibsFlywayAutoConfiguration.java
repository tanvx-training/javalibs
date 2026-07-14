package io.javalibs.persistence.autoconfigure;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Applies the platform's production-safe Flyway defaults on top of Spring
 * Boot's Flyway auto-configuration:
 *
 * <ul>
 *   <li>{@code clean} disabled (accidental {@code flyway:clean} in production
 *       drops the whole schema);</li>
 *   <li>migration naming validated ({@code V<version>__<description>.sql});</li>
 *   <li>out-of-order migrations rejected (linear history);</li>
 *   <li>baseline-on-migrate off (explicit opt-in for legacy schemas).</li>
 * </ul>
 *
 * <p>Each default can be overridden via {@code javalibs.persistence.flyway.*};
 * everything else (locations, datasource, placeholders) stays standard Spring
 * Boot {@code spring.flyway.*} configuration.</p>
 */
@AutoConfiguration(beforeName = "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration")
@ConditionalOnClass(Flyway.class)
@ConditionalOnProperty(prefix = "javalibs.persistence.flyway", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(PersistenceProperties.class)
public class JavalibsFlywayAutoConfiguration {

    /**
     * The customizer carrying the javalibs defaults; runs when Spring Boot builds
     * the Flyway configuration.
     *
     * @param properties the bound persistence properties
     * @return the customizer
     */
    @Bean
    @ConditionalOnMissingBean(name = "javalibsFlywayDefaultsCustomizer")
    public FlywayConfigurationCustomizer javalibsFlywayDefaultsCustomizer(
            PersistenceProperties properties) {
        return configuration -> configuration
                .cleanDisabled(properties.isCleanDisabled())
                .validateMigrationNaming(properties.isValidateMigrationNaming())
                .outOfOrder(properties.isOutOfOrder())
                .baselineOnMigrate(properties.isBaselineOnMigrate());
    }
}
