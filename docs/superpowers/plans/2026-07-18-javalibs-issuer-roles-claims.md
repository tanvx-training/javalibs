# javalibs-security-issuer: Roles + Custom Claims Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let `javalibs-security-issuer` mint access tokens that carry a `roles` claim (and arbitrary custom claims), so downstream services using `@RequireRole` on issued tokens work — the prerequisite for certi-master's `identity` context (Phase 2).

**Architecture:** Backward-compatible enrichment of three value types and one service. `StoredCredentials` gains `roles` + `extraClaims`; `TokenIssuer` gains a 5-arg `issue(...)` overload that writes the roles claim (under the configured claim name) plus custom claims; `AuthenticationService` threads those through on both login and refresh; `JwtIssuerConfig` learns the roles-claim name; the Boot auto-config wires it from `javalibs.security.jwt.roles-claim`. Every existing 3-arg/5-arg call site keeps compiling and behaving identically (new inputs default to empty).

**Tech Stack:** Java 21, JJWT, JUnit 5 + AssertJ + Mockito, Spring Boot 3.5.3 auto-configuration, Maven.

## Global Constraints

- Java 21; **no Lombok**; records preferred. (verbatim from javalibs conventions)
- README/docs in Vietnamese; **Javadoc in English**.
- Every change **backward compatible**: existing constructors/method signatures keep working; new fields default to empty (`Set.of()` / `Map.of()`), never null.
- Module: `io.javalibs:javalibs-security-issuer:1.0.0-SNAPSHOT` (and `-autoconfigure`).
- Build/test from repo root: `cd /Users/tanvx/Dev/javalibs-root` then `mvn -pl <module> -am test -q` (offline-friendly; javalibs is SNAPSHOT-from-source).
- When adding/changing a module surface, update `docs/modules/security.md` **and** `docs/configuration-reference.md` in the same change (contributing.md checklist).

---

### Task 1: `StoredCredentials` carries roles + custom claims

**Files:**
- Modify: `javalibs-security/javalibs-security-issuer/src/main/java/io/javalibs/security/issuer/StoredCredentials.java`
- Test: `javalibs-security/javalibs-security-issuer/src/test/java/io/javalibs/security/issuer/StoredCredentialsTest.java` (create)

**Interfaces:**
- Consumes: nothing new.
- Produces: `StoredCredentials(String userId, String username, String email, String passwordHash, boolean enabled, Set<String> roles, Map<String,Object> extraClaims)` — canonical 7-arg constructor (null-normalizing, defensive-copy), **plus** a 5-arg convenience constructor `StoredCredentials(userId, username, email, passwordHash, enabled)` delegating with empty roles/claims. Accessors `roles()` and `extraClaims()` never return null.

- [ ] **Step 1: Write the failing test**

Create `StoredCredentialsTest.java`:

```java
package io.javalibs.security.issuer;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StoredCredentialsTest {

    @Test
    void fiveArgConstructorDefaultsRolesAndClaimsToEmpty() {
        StoredCredentials c = new StoredCredentials("u1", "alice", "a@x.io", "{bcrypt}h", true);
        assertThat(c.roles()).isEmpty();
        assertThat(c.extraClaims()).isEmpty();
    }

    @Test
    void nullRolesAndClaimsAreNormalizedToEmpty() {
        StoredCredentials c = new StoredCredentials("u1", "alice", "a@x.io", "{bcrypt}h", true,
                null, null);
        assertThat(c.roles()).isEmpty();
        assertThat(c.extraClaims()).isEmpty();
    }

    @Test
    void rolesAndClaimsRoundTripAndAreDefensivelyCopied() {
        Set<String> roles = new java.util.HashSet<>(Set.of("ADMIN", "USER"));
        Map<String, Object> claims = new java.util.HashMap<>(Map.of("tenant", "t1"));
        StoredCredentials c = new StoredCredentials("u1", "alice", "a@x.io", "{bcrypt}h", true,
                roles, claims);

        roles.clear();
        claims.clear();

        assertThat(c.roles()).containsExactlyInAnyOrder("ADMIN", "USER");
        assertThat(c.extraClaims()).containsEntry("tenant", "t1");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/tanvx/Dev/javalibs-root && mvn -pl javalibs-security/javalibs-security-issuer -am test -q -Dtest=StoredCredentialsTest`
Expected: FAIL — compilation error (7-arg constructor / `roles()` / `extraClaims()` do not exist).

- [ ] **Step 3: Write minimal implementation**

Replace the body of `StoredCredentials.java` with:

```java
package io.javalibs.security.issuer;

import java.util.Map;
import java.util.Set;

/**
 * A user's stored authentication credentials as read from the identity store.
 *
 * @param userId the stable user identifier (embedded in the access token's {@code sub} claim)
 * @param username the login username
 * @param email the user's email address (may be null)
 * @param passwordHash the hashed password, as produced by {@link PasswordHasher#hash(String)}
 * @param enabled whether the account is allowed to authenticate; disabled accounts are
 *     rejected by {@link AuthenticationService} on both login and refresh
 * @param roles the roles written into the access token's roles claim; never null (empty when
 *     omitted)
 * @param extraClaims additional custom claims written into the access token; never null
 *     (empty when omitted). Reserved and already-mapped claim names are ignored by
 *     {@link TokenIssuer}.
 */
public record StoredCredentials(
        String userId, String username, String email, String passwordHash, boolean enabled,
        Set<String> roles, Map<String, Object> extraClaims) {

    public StoredCredentials {
        roles = (roles == null) ? Set.of() : Set.copyOf(roles);
        extraClaims = (extraClaims == null) ? Map.of() : Map.copyOf(extraClaims);
    }

    /**
     * Convenience constructor for a credential without roles or custom claims.
     *
     * @param userId the stable user identifier
     * @param username the login username
     * @param email the user's email address (may be null)
     * @param passwordHash the hashed password
     * @param enabled whether the account may authenticate
     */
    public StoredCredentials(String userId, String username, String email, String passwordHash,
            boolean enabled) {
        this(userId, username, email, passwordHash, enabled, Set.of(), Map.of());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /Users/tanvx/Dev/javalibs-root && mvn -pl javalibs-security/javalibs-security-issuer -am test -q -Dtest=StoredCredentialsTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
cd /Users/tanvx/Dev/javalibs-root
git add javalibs-security/javalibs-security-issuer/src/main/java/io/javalibs/security/issuer/StoredCredentials.java \
        javalibs-security/javalibs-security-issuer/src/test/java/io/javalibs/security/issuer/StoredCredentialsTest.java
git commit -m "feat(security-issuer): StoredCredentials mang roles + extraClaims (tương thích ngược)"
```

---

### Task 2: `JwtIssuerConfig` knows the roles-claim name

**Files:**
- Modify: `javalibs-security/javalibs-security-issuer/src/main/java/io/javalibs/security/issuer/JwtIssuerConfig.java`
- Test: `javalibs-security/javalibs-security-issuer/src/test/java/io/javalibs/security/issuer/JwtIssuerConfigTest.java` (create)

**Interfaces:**
- Consumes: nothing new.
- Produces: `JwtIssuerConfig.rolesClaim()` accessor (defaults to `"roles"`, blank normalized to default); `JwtIssuerConfig.Builder.rolesClaim(String)`. Canonical record constructor gains a trailing `String rolesClaim` parameter (constructed only via the builder).

- [ ] **Step 1: Write the failing test**

Create `JwtIssuerConfigTest.java`:

```java
package io.javalibs.security.issuer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtIssuerConfigTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void rolesClaimDefaultsToRoles() {
        JwtIssuerConfig config = JwtIssuerConfig.builder().hmacSecret(SECRET).build();
        assertThat(config.rolesClaim()).isEqualTo("roles");
    }

    @Test
    void rolesClaimCanBeCustomised() {
        JwtIssuerConfig config = JwtIssuerConfig.builder()
                .hmacSecret(SECRET)
                .rolesClaim("authorities")
                .build();
        assertThat(config.rolesClaim()).isEqualTo("authorities");
    }

    @Test
    void blankRolesClaimFallsBackToDefault() {
        JwtIssuerConfig config = JwtIssuerConfig.builder()
                .hmacSecret(SECRET)
                .rolesClaim("  ")
                .build();
        assertThat(config.rolesClaim()).isEqualTo("roles");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/tanvx/Dev/javalibs-root && mvn -pl javalibs-security/javalibs-security-issuer -am test -q -Dtest=JwtIssuerConfigTest`
Expected: FAIL — compilation error (`rolesClaim()` / `Builder.rolesClaim` do not exist).

- [ ] **Step 3: Write minimal implementation**

In `JwtIssuerConfig.java`:

1. Add `rolesClaim` to the record components (append after `emailClaim`):

```java
public record JwtIssuerConfig(
        String hmacSecret,
        String rsaPrivateKeyPem,
        String issuer,
        String audience,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        String usernameClaim,
        String emailClaim,
        String rolesClaim) {
```

2. Add the default constant near the others:

```java
    public static final String DEFAULT_ROLES_CLAIM = "roles";
```

3. Normalize it in the compact constructor (add after the `emailClaim` line):

```java
        rolesClaim = isBlank(rolesClaim) ? DEFAULT_ROLES_CLAIM : rolesClaim;
```

4. Add the builder field (with default):

```java
        private String rolesClaim = DEFAULT_ROLES_CLAIM;
```

5. Add the builder setter (place next to `emailClaim(...)`):

```java
        /**
         * Sets the claim name for roles (defaults to "roles").
         *
         * @param rolesClaim the custom roles claim name
         * @return this builder
         */
        public Builder rolesClaim(String rolesClaim) {
            this.rolesClaim = rolesClaim;
            return this;
        }
```

6. Pass it in `build()`:

```java
        public JwtIssuerConfig build() {
            return new JwtIssuerConfig(hmacSecret, rsaPrivateKeyPem, issuer, audience,
                    accessTokenTtl, refreshTokenTtl, usernameClaim, emailClaim, rolesClaim);
        }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /Users/tanvx/Dev/javalibs-root && mvn -pl javalibs-security/javalibs-security-issuer -am test -q -Dtest=JwtIssuerConfigTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
cd /Users/tanvx/Dev/javalibs-root
git add javalibs-security/javalibs-security-issuer/src/main/java/io/javalibs/security/issuer/JwtIssuerConfig.java \
        javalibs-security/javalibs-security-issuer/src/test/java/io/javalibs/security/issuer/JwtIssuerConfigTest.java
git commit -m "feat(security-issuer): JwtIssuerConfig có rolesClaim (mặc định roles)"
```

---

### Task 3: `TokenIssuer` emits roles + custom claims

**Files:**
- Modify: `javalibs-security/javalibs-security-issuer/src/main/java/io/javalibs/security/issuer/TokenIssuer.java`
- Test: `javalibs-security/javalibs-security-issuer/src/test/java/io/javalibs/security/issuer/TokenIssuerTest.java` (extend)

**Interfaces:**
- Consumes: `JwtIssuerConfig.rolesClaim()` (Task 2).
- Produces: new overload `IssuedToken issue(String userId, String username, String email, Set<String> roles, Map<String,Object> extraClaims)`. The existing 3-arg `issue(userId, username, email)` delegates with empty roles/claims (unchanged behavior). Roles are written as a JSON array under `config.rolesClaim()` only when non-empty; `extraClaims` entries are written except reserved names (`sub`, `iss`, `aud`, `exp`, `nbf`, `iat`, `jti`) and the mapped names (`config.usernameClaim()`, `config.emailClaim()`, `config.rolesClaim()`).

- [ ] **Step 1: Write the failing test**

Append these tests to `TokenIssuerTest.java` (imports to add at top: `java.util.List`, `java.util.Map`, `java.util.Set`):

```java
    @Test
    void issuedTokenCarriesRolesReadableByValidator() {
        JwtIssuerConfig config = JwtIssuerConfig.builder().hmacSecret(SECRET).build();
        TokenIssuer issuer = new TokenIssuer(config, Clock.systemUTC());

        TokenIssuer.IssuedToken issued = issuer.issue(
                "u1", "alice", "alice@example.com", Set.of("ADMIN", "USER"), Map.of());

        JwtTokenValidator validator = new JwtTokenValidator(
                JwtValidationConfig.builder().hmacSecret(SECRET).build());
        UserContext user = validator.validate(issued.token());
        assertThat(user.roles()).containsExactlyInAnyOrder("ADMIN", "USER");
    }

    @Test
    void issuedTokenCarriesCustomClaims() {
        JwtIssuerConfig config = JwtIssuerConfig.builder().hmacSecret(SECRET).build();
        TokenIssuer issuer = new TokenIssuer(config, Clock.systemUTC());

        TokenIssuer.IssuedToken issued = issuer.issue(
                "u1", "alice", null, Set.of(), Map.of("tenant", "acme"));

        JwtTokenValidator validator = new JwtTokenValidator(
                JwtValidationConfig.builder().hmacSecret(SECRET).build());
        UserContext user = validator.validate(issued.token());
        assertThat(user.attributes()).containsEntry("tenant", "acme");
    }

    @Test
    void customClaimsCannotOverrideReservedOrMappedClaims() {
        JwtIssuerConfig config = JwtIssuerConfig.builder().hmacSecret(SECRET).build();
        TokenIssuer issuer = new TokenIssuer(config, Clock.systemUTC());

        TokenIssuer.IssuedToken issued = issuer.issue(
                "u1", "alice", "alice@example.com",
                Set.of("ADMIN"),
                Map.of("sub", "attacker", "roles", List.of("HACKER"), "preferred_username", "eve"));

        JwtTokenValidator validator = new JwtTokenValidator(
                JwtValidationConfig.builder().hmacSecret(SECRET).build());
        UserContext user = validator.validate(issued.token());
        assertThat(user.userId()).isEqualTo("u1");
        assertThat(user.username()).isEqualTo("alice");
        assertThat(user.roles()).containsExactly("ADMIN");
    }

    @Test
    void emptyRolesProduceNoRolesClaim() {
        JwtIssuerConfig config = JwtIssuerConfig.builder().hmacSecret(SECRET).build();
        TokenIssuer issuer = new TokenIssuer(config, Clock.systemUTC());

        TokenIssuer.IssuedToken issued = issuer.issue("u1", "alice", null, Set.of(), Map.of());

        JwtTokenValidator validator = new JwtTokenValidator(
                JwtValidationConfig.builder().hmacSecret(SECRET).build());
        UserContext user = validator.validate(issued.token());
        assertThat(user.roles()).isEmpty();
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/tanvx/Dev/javalibs-root && mvn -pl javalibs-security/javalibs-security-issuer -am test -q -Dtest=TokenIssuerTest`
Expected: FAIL — compilation error (5-arg `issue(...)` does not exist).

- [ ] **Step 3: Write minimal implementation**

In `TokenIssuer.java`:

1. Add imports at the top (near the other `java.util` imports): `import java.util.List;`, `import java.util.Map;`, `import java.util.Set;`.

2. Add a reserved-name constant inside the class (near the top of the class body):

```java
    private static final Set<String> RESERVED_CLAIMS =
            Set.of("sub", "iss", "aud", "exp", "nbf", "iat", "jti");
```

3. Change the existing 3-arg method to delegate (replace its body):

```java
    public IssuedToken issue(String userId, String username, String email) {
        return issue(userId, username, email, Set.of(), Map.of());
    }
```

4. Add the new 5-arg overload directly below it. This is the old body plus roles/extra-claims emission:

```java
    /**
     * Issues a signed access token carrying roles and optional custom claims.
     *
     * @param userId the stable user id, written to the {@code sub} claim (must not be null)
     * @param username the username, written under the configured username claim (skipped when null)
     * @param email the email, written under the configured email claim (skipped when null)
     * @param roles the roles, written as a JSON array under the configured roles claim (skipped
     *     when null or empty)
     * @param extraClaims additional claims to include; reserved names ({@code sub}, {@code iss},
     *     {@code aud}, {@code exp}, {@code nbf}, {@code iat}, {@code jti}) and the mapped
     *     username/email/roles claim names are ignored so they cannot be spoofed
     * @return the signed token with its id and expiry
     */
    public IssuedToken issue(String userId, String username, String email,
            Set<String> roles, Map<String, Object> extraClaims) {
        Objects.requireNonNull(userId, "userId must not be null");
        String tokenId = UUID.randomUUID().toString();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(config.accessTokenTtl());

        JwtBuilder builder = Jwts.builder()
                .subject(userId)
                .id(tokenId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt));
        if (config.issuer() != null && !config.issuer().isBlank()) {
            builder.issuer(config.issuer());
        }
        if (config.audience() != null && !config.audience().isBlank()) {
            builder.audience().add(config.audience()).and();
        }
        if (username != null) {
            builder.claim(config.usernameClaim(), username);
        }
        if (email != null) {
            builder.claim(config.emailClaim(), email);
        }
        if (roles != null && !roles.isEmpty()) {
            builder.claim(config.rolesClaim(), List.copyOf(roles));
        }
        if (extraClaims != null) {
            for (Map.Entry<String, Object> entry : extraClaims.entrySet()) {
                String name = entry.getKey();
                if (name == null || RESERVED_CLAIMS.contains(name)
                        || name.equals(config.usernameClaim())
                        || name.equals(config.emailClaim())
                        || name.equals(config.rolesClaim())) {
                    continue;
                }
                builder.claim(name, entry.getValue());
            }
        }
        if (signingKey instanceof SecretKey secretKey) {
            builder.signWith(secretKey, Jwts.SIG.HS256);
        } else {
            builder.signWith((java.security.PrivateKey) signingKey, Jwts.SIG.RS256);
        }
        return new IssuedToken(builder.compact(), tokenId, expiresAt);
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /Users/tanvx/Dev/javalibs-root && mvn -pl javalibs-security/javalibs-security-issuer -am test -q -Dtest=TokenIssuerTest`
Expected: PASS (all original + 4 new tests).

- [ ] **Step 5: Commit**

```bash
cd /Users/tanvx/Dev/javalibs-root
git add javalibs-security/javalibs-security-issuer/src/main/java/io/javalibs/security/issuer/TokenIssuer.java \
        javalibs-security/javalibs-security-issuer/src/test/java/io/javalibs/security/issuer/TokenIssuerTest.java
git commit -m "feat(security-issuer): TokenIssuer phát roles claim + custom claims (chặn ghi đè claim reserved)"
```

---

### Task 4: `AuthenticationService` threads roles/claims into login + refresh

**Files:**
- Modify: `javalibs-security/javalibs-security-issuer/src/main/java/io/javalibs/security/issuer/AuthenticationService.java:170-171` (the `tokenIssuer.issue(...)` call inside `issuePairWithRefreshToken`)
- Test: `javalibs-security/javalibs-security-issuer/src/test/java/io/javalibs/security/issuer/AuthenticationServiceTest.java` (extend)

**Interfaces:**
- Consumes: `StoredCredentials.roles()`/`.extraClaims()` (Task 1), 5-arg `TokenIssuer.issue(...)` (Task 3).
- Produces: no signature change — `login(...)` and `refresh(...)` now mint tokens carrying the user's roles/claims.

- [ ] **Step 1: Write the failing test**

Append to `AuthenticationServiceTest.java` (add imports: `io.javalibs.security.JwtTokenValidator`, `io.javalibs.security.JwtValidationConfig`, `io.javalibs.security.UserContext`, `java.util.Set`):

```java
    @Test
    void loginTokenCarriesUserRoles() {
        StoredCredentials admin = new StoredCredentials(
                "u9", "carol", "carol@x.io", hasher.hash("s3cret"), true,
                Set.of("ADMIN"), java.util.Map.of());
        CredentialsStore store = new CredentialsStore() {
            @Override public Optional<StoredCredentials> findByUsername(String u) {
                return "carol".equals(u) ? Optional.of(admin) : Optional.empty();
            }
            @Override public Optional<StoredCredentials> findByUserId(String id) {
                return "u9".equals(id) ? Optional.of(admin) : Optional.empty();
            }
        };
        JwtIssuerConfig config = JwtIssuerConfig.builder().hmacSecret(SECRET).build();
        AuthenticationService svc = new AuthenticationService(store, refreshTokens, hasher,
                new TokenIssuer(config, Clock.systemUTC()), config, Clock.systemUTC(), blacklist);

        TokenPair pair = svc.login("carol", "s3cret");

        UserContext user = new JwtTokenValidator(
                JwtValidationConfig.builder().hmacSecret(SECRET).build())
                .validate(pair.accessToken());
        assertThat(user.roles()).containsExactly("ADMIN");

        TokenPair refreshed = svc.refresh(pair.refreshToken());
        UserContext refreshedUser = new JwtTokenValidator(
                JwtValidationConfig.builder().hmacSecret(SECRET).build())
                .validate(refreshed.accessToken());
        assertThat(refreshedUser.roles()).containsExactly("ADMIN");
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/tanvx/Dev/javalibs-root && mvn -pl javalibs-security/javalibs-security-issuer -am test -q -Dtest=AuthenticationServiceTest#loginTokenCarriesUserRoles`
Expected: FAIL — `user.roles()` is empty (the service still calls the 3-arg `issue`).

- [ ] **Step 3: Write minimal implementation**

In `AuthenticationService.java`, inside `issuePairWithRefreshToken`, change the token-issue call:

```java
        TokenIssuer.IssuedToken access = tokenIssuer.issue(
                user.userId(), user.username(), user.email(), user.roles(), user.extraClaims());
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /Users/tanvx/Dev/javalibs-root && mvn -pl javalibs-security/javalibs-security-issuer -am test -q -Dtest=AuthenticationServiceTest`
Expected: PASS (all existing + new test).

- [ ] **Step 5: Commit**

```bash
cd /Users/tanvx/Dev/javalibs-root
git add javalibs-security/javalibs-security-issuer/src/main/java/io/javalibs/security/issuer/AuthenticationService.java \
        javalibs-security/javalibs-security-issuer/src/test/java/io/javalibs/security/issuer/AuthenticationServiceTest.java
git commit -m "feat(security-issuer): login + refresh mint token mang roles/claims của user"
```

---

### Task 5: Auto-config wires the roles-claim name from properties

**Files:**
- Modify: `javalibs-security/javalibs-security-spring-boot-autoconfigure/src/main/java/io/javalibs/security/autoconfigure/SecurityIssuerAutoConfiguration.java:84-93` (the `JwtIssuerConfig.builder()` chain in `javalibsJwtIssuerConfig`)
- Test: `javalibs-security/javalibs-security-spring-boot-autoconfigure/src/test/java/io/javalibs/security/autoconfigure/SecurityIssuerAutoConfigurationTest.java` (create)

**Interfaces:**
- Consumes: `SecurityProperties.Jwt.getRolesClaim()` (existing, default `"roles"`), `JwtIssuerConfig.Builder.rolesClaim(...)` (Task 2).
- Produces: the `javalibsJwtIssuerConfig` bean now sets `rolesClaim` from `javalibs.security.jwt.roles-claim`, so issued tokens use the same claim name the validator reads.

- [ ] **Step 1: Write the failing test**

Create `SecurityIssuerAutoConfigurationTest.java`:

```java
package io.javalibs.security.autoconfigure;

import io.javalibs.security.issuer.JwtIssuerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityIssuerAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SecurityIssuerAutoConfiguration.class))
            .withPropertyValues("javalibs.security.jwt.secret=0123456789abcdef0123456789abcdef");

    @Test
    void jwtIssuerConfigUsesDefaultRolesClaim() {
        runner.run(ctx -> {
            JwtIssuerConfig config = ctx.getBean(JwtIssuerConfig.class);
            assertThat(config.rolesClaim()).isEqualTo("roles");
        });
    }

    @Test
    void jwtIssuerConfigHonoursCustomRolesClaim() {
        runner.withPropertyValues("javalibs.security.jwt.roles-claim=authorities")
                .run(ctx -> {
                    JwtIssuerConfig config = ctx.getBean(JwtIssuerConfig.class);
                    assertThat(config.rolesClaim()).isEqualTo("authorities");
                });
    }
}
```

Note: `SecurityIssuerAutoConfiguration` uses `@EnableConfigurationProperties` for `SecurityProperties`/`IssuerProperties`. If the context fails to find those beans, add `.withPropertyValues(...)` only — do not add extra config classes; the auto-config declares its own properties. If a bean-not-found error appears for properties, extend the runner with `.withInitializer(new ConfigDataApplicationContextInitializer())` — but try without first.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/tanvx/Dev/javalibs-root && mvn -pl javalibs-security/javalibs-security-spring-boot-autoconfigure -am test -q -Dtest=SecurityIssuerAutoConfigurationTest`
Expected: FAIL — `jwtIssuerConfigUsesDefaultRolesClaim` may pass by default coincidence, but `jwtIssuerConfigHonoursCustomRolesClaim` FAILS (`rolesClaim` is `"roles"`, not `"authorities"`, because the builder call never sets it).

- [ ] **Step 3: Write minimal implementation**

In `SecurityIssuerAutoConfiguration.javalibsJwtIssuerConfig`, add `.rolesClaim(jwt.getRolesClaim())` to the builder chain (place next to `.usernameClaim(...)`):

```java
        return JwtIssuerConfig.builder()
                .hmacSecret(hasPrivateKey ? null : jwt.getSecret())
                .rsaPrivateKeyPem(issuer.getPrivateKey())
                .issuer(jwt.getIssuer())
                .audience(jwt.getAudience())
                .accessTokenTtl(issuer.getAccessTokenTtl())
                .refreshTokenTtl(issuer.getRefreshTokenTtl())
                .rolesClaim(jwt.getRolesClaim())
                .usernameClaim(jwt.getUsernameClaim())
                .emailClaim(jwt.getEmailClaim())
                .build();
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /Users/tanvx/Dev/javalibs-root && mvn -pl javalibs-security/javalibs-security-spring-boot-autoconfigure -am test -q -Dtest=SecurityIssuerAutoConfigurationTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
cd /Users/tanvx/Dev/javalibs-root
git add javalibs-security/javalibs-security-spring-boot-autoconfigure/src/main/java/io/javalibs/security/autoconfigure/SecurityIssuerAutoConfiguration.java \
        javalibs-security/javalibs-security-spring-boot-autoconfigure/src/test/java/io/javalibs/security/autoconfigure/SecurityIssuerAutoConfigurationTest.java
git commit -m "feat(security-issuer): auto-config đồng bộ rolesClaim từ javalibs.security.jwt.roles-claim"
```

---

### Task 6: Docs + full-module verify + install

**Files:**
- Modify: `docs/modules/security.md` (the "Phát hành token (issuer)" section)
- Modify: `docs/configuration-reference.md` (issuer properties + note that issued tokens carry roles)
- No test file — this task's gate is the whole security reactor building + installing green.

**Interfaces:**
- Consumes: everything above.
- Produces: published SNAPSHOT so `certi-platform` (Phase 2 Plan 2) can consume the enhanced API.

- [ ] **Step 1: Update `docs/modules/security.md`**

In the issuer section, after the paragraph describing `AuthenticationService`, add a subsection:

```markdown
### Roles & custom claims trong token phát hành

`TokenIssuer` phát access token mang **roles** và **claim tùy ý** lấy từ `StoredCredentials`:

- `StoredCredentials(userId, username, email, passwordHash, enabled, roles, extraClaims)` — `roles` (`Set<String>`) ghi vào claim theo tên `javalibs.security.jwt.roles-claim` (mặc định `roles`, JSON array) khi không rỗng; `extraClaims` (`Map<String,Object>`) ghi các claim còn lại. Constructor 5 tham số cũ vẫn dùng được (roles/claims rỗng).
- `TokenIssuer.issue(userId, username, email, roles, extraClaims)` là overload mới; overload 3 tham số cũ giữ nguyên (không roles).
- Claim **reserved** (`sub`, `iss`, `aud`, `exp`, `nbf`, `iat`, `jti`) và các tên claim đã map (username/email/roles) **không thể bị `extraClaims` ghi đè** — chống giả mạo.
- `AuthenticationService.login`/`refresh` tự truyền `roles`/`extraClaims` của user xuống — service validate token đọc `roles` qua `javalibs.security.jwt.roles-claim` như thường, `@RequireRole` hoạt động ngay.
```

- [ ] **Step 2: Update `docs/configuration-reference.md`**

Find the issuer properties table (`javalibs.security.issuer.*`). Add one row documenting that the roles claim name is shared with validation:

```markdown
| `javalibs.security.jwt.roles-claim` | String | `roles` | Tên claim chứa roles — **dùng chung** cho cả validate (JwtTokenValidator) lẫn phát hành (TokenIssuer). Token do issuer phát mang roles của user dưới claim này. |
```

(If a `roles-claim` row already exists in the validation section, add the "dùng chung cho phát hành" clause to it instead of duplicating.)

- [ ] **Step 3: Full security reactor test**

Run: `cd /Users/tanvx/Dev/javalibs-root && mvn -pl javalibs-security -amd test -q`
Expected: PASS — every security submodule (core, spring, issuer, autoconfigure, test) green. In particular the existing `AuthEndpointsTest` and `AuthenticationServiceTest` still pass (backward compatibility confirmed).

- [ ] **Step 4: Install the SNAPSHOT**

Run: `cd /Users/tanvx/Dev/javalibs-root && mvn -pl javalibs-security/javalibs-security-issuer,javalibs-security/javalibs-security-spring-boot-autoconfigure -am install -q -DskipTests`
Expected: `BUILD SUCCESS`; artifacts `javalibs-security-issuer` + `-autoconfigure` `1.0.0-SNAPSHOT` in the local `~/.m2`.

- [ ] **Step 5: Commit**

```bash
cd /Users/tanvx/Dev/javalibs-root
git add docs/modules/security.md docs/configuration-reference.md
git commit -m "docs(security): token phát hành mang roles + custom claims (issuer)"
```

---

## Self-Review

**1. Spec coverage (§2 of the spec):**
- "StoredCredentials thêm roles + extraClaims, tương thích ngược" → Task 1. ✓
- "TokenIssuer overload emit roles claim (config.rolesClaim()) + extraClaims, không ghi đè reserved" → Task 3. ✓
- "AuthenticationService truyền roles/extraClaims (login + refresh)" → Task 4. ✓
- "JwtIssuerConfig có rolesClaim, mặc định roles" → Task 2. ✓
- "auto-config wire rolesClaim" → Task 5. ✓
- "Test: login/refresh chứa roles; extraClaims; overload cũ ra token không roles; roles rỗng không claim thừa" → Tasks 3 & 4 tests. ✓
- "Cập nhật security.md + configuration-reference.md; install" → Task 6. ✓

**2. Placeholder scan:** No TBD/TODO; every code step shows full code. The Task 5 note about `ConfigDataApplicationContextInitializer` is a conditional fallback, not a placeholder — the primary path is complete. ✓

**3. Type consistency:** `roles` is `Set<String>` and `extraClaims` is `Map<String,Object>` in `StoredCredentials` (Task 1), `TokenIssuer.issue(...)` (Task 3), and the `AuthenticationService` call (Task 4). `rolesClaim` is `String` in `JwtIssuerConfig` (Task 2), read via `config.rolesClaim()` (Task 3) and `jwt.getRolesClaim()` (Task 5). `IssuedToken` unchanged. Consistent. ✓
