package io.javalibs.cqrs.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for the javalibs CQRS auto-configuration,
 * bound from the {@code javalibs.cqrs.*} namespace.
 *
 * @param enabled whether the CQRS auto-configuration is active; defaults to {@code true}
 */
@ConfigurationProperties(prefix = "javalibs.cqrs")
public record CqrsProperties(@DefaultValue("true") boolean enabled) {
}
