# javalibs-security-issuer: `issueFor` (token pair for a pre-authenticated user) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let `AuthenticationService` mint an access + refresh token pair for a user who was authenticated by means *other than* a password — the prerequisite for certi-master's OAuth2 social login (and reusable for SSO / admin impersonation).

**Architecture:** Add one public method, `AuthenticationService.issueFor(StoredCredentials)`, that delegates to the existing private `issuePair(...)` with a fresh token family. It reuses the exact same access-token issuance (roles + custom claims already threaded through from the earlier enhancement) and refresh-token creation/storage as password `login()`. Purely additive — no existing signature or behavior changes.

**Tech Stack:** Java 21, JJWT, JUnit 5 + AssertJ, Maven.

## Global Constraints

- Java 21; **no Lombok**; records preferred. Javadoc in English; docs Vietnamese.
- Purely additive and **backward compatible**: no change to `login`/`refresh`/`logout` behavior or signatures.
- Module `io.javalibs:javalibs-security-issuer:1.0.0-SNAPSHOT`.
- Build/test from repo root: `cd /Users/tanvx/Dev/javalibs-root` then `mvn -pl javalibs-security/javalibs-security-issuer -am test -q` (set `JAVA_HOME` if unset; the `-am` reactor builds upstream javalibs from source on first run — expected).
- When changing a module surface, update `docs/modules/security.md` (issuer section) in the same change.

---

### Task 1: `AuthenticationService.issueFor(StoredCredentials)`

**Files:**
- Modify: `javalibs-security/javalibs-security-issuer/src/main/java/io/javalibs/security/issuer/AuthenticationService.java`
- Test: `javalibs-security/javalibs-security-issuer/src/test/java/io/javalibs/security/issuer/AuthenticationServiceTest.java` (extend)

**Interfaces:**
- Consumes: existing private `issuePair(StoredCredentials, String familyId)`.
- Produces: `public TokenPair issueFor(StoredCredentials user)` — issues an access + refresh pair (new family) for an already-authenticated user, with no password check. The access token carries `user.roles()`/`user.extraClaims()` (same as `login`); the refresh token is stored (hashed) so it can be rotated/revoked like any other.

- [ ] **Step 1: Write the failing test**

Append to `AuthenticationServiceTest.java` (imports already present from the existing roles test: `io.javalibs.security.JwtTokenValidator`, `JwtValidationConfig`, `UserContext`, `java.util.Set`, `java.util.Map`, `Clock`):

```java
    @Test
    void issueForMintsTokenPairWithRolesWithoutPassword() {
        StoredCredentials carol = new StoredCredentials(
                "u9", "carol", "carol@x.io", hasher.hash("irrelevant"), true,
                Set.of("ADMIN"), Map.of());
        JwtIssuerConfig config = JwtIssuerConfig.builder().hmacSecret(SECRET).build();
        AuthenticationService svc = new AuthenticationService(credentials, refreshTokens, hasher,
                new TokenIssuer(config, Clock.systemUTC()), config, Clock.systemUTC(), blacklist);

        TokenPair pair = svc.issueFor(carol);

        // access token is valid and carries the user's roles — no password was involved
        UserContext user = new JwtTokenValidator(
                JwtValidationConfig.builder().hmacSecret(SECRET).build())
                .validate(pair.accessToken());
        assertThat(user.userId()).isEqualTo("u9");
        assertThat(user.roles()).containsExactly("ADMIN");
        // refresh token is stored by hash (never the raw value), so it can be rotated/revoked
        assertThat(refreshTokens.byHash)
                .containsKey(RefreshTokens.hash(pair.refreshToken()))
                .doesNotContainKey(pair.refreshToken());
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd /Users/tanvx/Dev/javalibs-root && mvn -pl javalibs-security/javalibs-security-issuer -am test -q -Dtest=AuthenticationServiceTest#issueForMintsTokenPairWithRolesWithoutPassword`
Expected: FAIL — compilation error (`issueFor` does not exist).

- [ ] **Step 3: Implement**

In `AuthenticationService.java`, add the public method (place it right after `login(...)`, before `refresh(...)`):

```java
    /**
     * Issues a fresh access + refresh token pair (a new token family) for a user who has
     * already been authenticated by some means <em>other than</em> a password — for example
     * an OAuth2 / OIDC social login, an enterprise SSO assertion, or an administrator
     * impersonation flow. No password is checked here: the caller is responsible for having
     * verified the principal's identity before calling this method.
     *
     * <p>The resulting access token carries the same {@code roles} and custom claims as a
     * password {@link #login(String, String)}, and the refresh token is stored (hashed) so it
     * participates in normal rotation/reuse-detection and can be revoked via {@link #logout}.
     *
     * @param user the stored credentials of the already-authenticated user (its
     *     {@code passwordHash} is ignored and may be null; {@code enabled} is NOT re-checked —
     *     validate the account state before calling)
     * @return a freshly issued access + refresh token pair in a new family
     */
    public TokenPair issueFor(StoredCredentials user) {
        return issuePair(user, UUID.randomUUID().toString());
    }
```

- [ ] **Step 4: Run to verify it passes**

Run: `cd /Users/tanvx/Dev/javalibs-root && mvn -pl javalibs-security/javalibs-security-issuer -am test -q -Dtest=AuthenticationServiceTest`
Expected: PASS — the new test plus all existing `AuthenticationServiceTest` cases (backward compatibility).

- [ ] **Step 5: Commit**

```bash
cd /Users/tanvx/Dev/javalibs-root
git add javalibs-security/javalibs-security-issuer/src/main/java/io/javalibs/security/issuer/AuthenticationService.java \
        javalibs-security/javalibs-security-issuer/src/test/java/io/javalibs/security/issuer/AuthenticationServiceTest.java
git commit -m "feat(security-issuer): AuthenticationService.issueFor — token pair cho user đã xác thực (OAuth/SSO)"
```

---

### Task 2: Docs + full-reactor verify + install

**Files:**
- Modify: `docs/modules/security.md` (issuer section)
- No test file — the gate is the whole security reactor building + installing green.

**Interfaces:**
- Consumes: Task 1.
- Produces: an installed SNAPSHOT so certi-master's OAuth2 plan can consume `issueFor`.

- [ ] **Step 1: Document `issueFor` in `docs/modules/security.md`**

In the issuer section, near the description of `AuthenticationService` (login/refresh/logout), add a short paragraph:

```markdown
#### Phát hành token cho user đã xác thực (không mật khẩu)

`AuthenticationService.issueFor(StoredCredentials user)` phát cặp access + refresh token
(family mới) cho một user đã được xác thực bằng **cách khác** không phải mật khẩu — ví dụ
đăng nhập xã hội OAuth2/OIDC, SSO doanh nghiệp, hoặc admin impersonation. Không kiểm tra mật
khẩu, không kiểm tra lại `enabled` (bên gọi phải tự xác minh danh tính + trạng thái tài khoản
trước). Access token mang `roles`/custom claims y như `login`; refresh token được lưu (hash)
nên vẫn rotation/reuse-detection và thu hồi được qua `logout`. Dùng cho service tự upsert user
từ nhà cung cấp OAuth rồi phát token của chính mình.
```

- [ ] **Step 2: Full security reactor test**

Run: `cd /Users/tanvx/Dev/javalibs-root && mvn -pl javalibs-security -amd test -q`
Expected: PASS — every security submodule green (core, spring, issuer, autoconfigure, test); in particular existing `AuthenticationServiceTest`, `TokenIssuerTest`, `AuthEndpointsTest` still pass.

- [ ] **Step 3: Install the SNAPSHOT**

Run: `cd /Users/tanvx/Dev/javalibs-root && mvn -pl javalibs-security/javalibs-security-issuer -am install -q -DskipTests`
Expected: `BUILD SUCCESS`; `javalibs-security-issuer:1.0.0-SNAPSHOT` refreshed in the local `~/.m2`.

- [ ] **Step 4: Commit**

```bash
cd /Users/tanvx/Dev/javalibs-root
git add docs/modules/security.md
git commit -m "docs(security): issueFor — phát token cho user đã xác thực (OAuth/SSO)"
```

---

## Self-Review

**1. Spec coverage:** The single design goal — a public method to mint a token pair for a pre-authenticated user, reusing the existing roles-aware `issuePair` — is implemented in Task 1 and documented + installed in Task 2. ✓

**2. Placeholder scan:** No TBD/TODO; every code step shows full code. ✓

**3. Type consistency:** `issueFor(StoredCredentials) → TokenPair` delegates to the existing `private TokenPair issuePair(StoredCredentials, String)`. `StoredCredentials` is the 7-arg record (roles + extraClaims) from the earlier enhancement; the access token emits roles via the same `issuePairWithRefreshToken` path used by `login`. `RefreshTokens.hash(...)` used in the test matches the storage in `issuePairWithRefreshToken`. ✓

## Next: certi-master OAuth2 (Plan 5B)
After this is merged/installed: certi-master identity adds `user_oauth_identity` (V5), `GoogleIdTokenVerifier` (Nimbus JwtDecoder / JWKS) + `GitHubOAuthClient` (RestClient) behind ports, `POST /api/v1/auth/oauth2/{google,github}` that verify → upsert `User`+`OAuthIdentity` → `credentialsStore.findByUserId(id)` → `AuthenticationService.issueFor(creds)` → return the token pair. Requires the `spring-security-oauth2-jose` dependency (Nimbus).
