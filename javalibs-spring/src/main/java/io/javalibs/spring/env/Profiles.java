package io.javalibs.spring.env;

import org.springframework.core.env.Environment;

/**
 * Conventional profile names used across services, with helpers to query the
 * active environment.
 */
public final class Profiles {

    public static final String LOCAL = "local";
    public static final String DEV = "dev";
    public static final String STAGING = "staging";
    public static final String PROD = "prod";
    public static final String TEST = "test";

    private Profiles() {
    }

    /** Returns {@code true} when the given profile is active. */
    public static boolean isActive(Environment environment, String profile) {
        return environment.matchesProfiles(profile);
    }

    /** Returns {@code true} when running in production. */
    public static boolean isProd(Environment environment) {
        return isActive(environment, PROD);
    }

    /** Returns {@code true} when running locally or in tests (safe for verbose diagnostics). */
    public static boolean isLocalOrTest(Environment environment) {
        return environment.matchesProfiles(LOCAL + " | " + TEST);
    }
}
