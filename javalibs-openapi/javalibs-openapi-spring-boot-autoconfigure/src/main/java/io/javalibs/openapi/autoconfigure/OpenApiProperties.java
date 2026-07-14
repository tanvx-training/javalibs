package io.javalibs.openapi.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for the standardized OpenAPI document
 * ({@code javalibs.openapi.*}).
 */
@ConfigurationProperties(prefix = "javalibs.openapi")
public class OpenApiProperties {

    /** Master switch for the javalibs OpenAPI auto-configuration. */
    private boolean enabled = true;

    /** API title; defaults to "&lt;spring.application.name&gt; API". */
    private String title;

    /** API description shown on the documentation landing page. */
    private String description;

    /** API version label. */
    private String version = "v1";

    /** Base URLs listed in the document (e.g. the gateway URL). */
    private List<String> servers = new ArrayList<>();

    private final Contact contact = new Contact();

    private final Security security = new Security();

    private final GlobalResponses globalResponses = new GlobalResponses();

    /** Team contact published in the document. */
    public static class Contact {

        /** Owning team name. */
        private String name;

        /** Team contact email. */
        private String email;

        /** Team page / runbook URL. */
        private String url;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    /** Security scheme settings ({@code javalibs.openapi.security.*}). */
    public static class Security {

        /** Whether the bearer-JWT scheme is declared and required globally. */
        private boolean enabled = true;

        /** Name of the security scheme component. */
        private String schemeName = "bearerAuth";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getSchemeName() {
            return schemeName;
        }

        public void setSchemeName(String schemeName) {
            this.schemeName = schemeName;
        }
    }

    /** Global error responses ({@code javalibs.openapi.global-responses.*}). */
    public static class GlobalResponses {

        /** Whether 400/401/403/404/409/500 are documented on every operation. */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public List<String> getServers() {
        return servers;
    }

    public void setServers(List<String> servers) {
        this.servers = servers;
    }

    public Contact getContact() {
        return contact;
    }

    public Security getSecurity() {
        return security;
    }

    public GlobalResponses getGlobalResponses() {
        return globalResponses;
    }
}
