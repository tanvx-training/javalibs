package io.javalibs.cache.autoconfigure;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Locale;

/**
 * Conditions gating the provider-specific cache auto-configurations on the
 * {@code javalibs.cache.provider} property ({@code auto} when unset).
 */
public final class ProviderConditions {

    static final String PROVIDER_PROPERTY = "javalibs.cache.provider";

    private ProviderConditions() {
    }

    private static String provider(ConditionContext context) {
        return context.getEnvironment()
                .getProperty(PROVIDER_PROPERTY, "auto")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    /** Matches when the provider allows Redis ({@code auto} or {@code redis}). */
    public static class RedisAllowed implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String provider = provider(context);
            return "auto".equals(provider) || "redis".equals(provider);
        }
    }

    /** Matches when the provider allows Caffeine ({@code auto} or {@code caffeine}). */
    public static class CaffeineAllowed implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String provider = provider(context);
            return "auto".equals(provider) || "caffeine".equals(provider);
        }
    }
}
