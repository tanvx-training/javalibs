package io.javalibs.persistence.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the javalibs persistence conventions
 * ({@code javalibs.persistence.flyway.*}).
 */
@ConfigurationProperties(prefix = "javalibs.persistence.flyway")
public class PersistenceProperties {

    /** Whether the javalibs Flyway defaults are applied. */
    private boolean enabled = true;

    /**
     * Disables {@code flyway clean} — a production-safety guard: clean drops every
     * object in the schema. Only lift this in throwaway environments.
     */
    private boolean cleanDisabled = true;

    /** Fails the migration when a file does not follow the V&lt;version&gt;__&lt;desc&gt;.sql naming. */
    private boolean validateMigrationNaming = true;

    /** Whether out-of-order migrations are accepted (keep false for linear history). */
    private boolean outOfOrder = false;

    /** Baseline existing (pre-Flyway) schemas on first migrate. */
    private boolean baselineOnMigrate = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isCleanDisabled() {
        return cleanDisabled;
    }

    public void setCleanDisabled(boolean cleanDisabled) {
        this.cleanDisabled = cleanDisabled;
    }

    public boolean isValidateMigrationNaming() {
        return validateMigrationNaming;
    }

    public void setValidateMigrationNaming(boolean validateMigrationNaming) {
        this.validateMigrationNaming = validateMigrationNaming;
    }

    public boolean isOutOfOrder() {
        return outOfOrder;
    }

    public void setOutOfOrder(boolean outOfOrder) {
        this.outOfOrder = outOfOrder;
    }

    public boolean isBaselineOnMigrate() {
        return baselineOnMigrate;
    }

    public void setBaselineOnMigrate(boolean baselineOnMigrate) {
        this.baselineOnMigrate = baselineOnMigrate;
    }
}
