package io.javalibs.authz.jpa.e2e;

import io.javalibs.authz.Scope;
import io.javalibs.authz.Subject;
import io.javalibs.authz.jpa.AuthzManagementService;
import io.javalibs.security.issuer.PasswordHasher;
import io.javalibs.test.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test exercising the full auth stack over real HTTP: login issues a
 * token pair, {@code @RequirePermission} enforces project-scoped authorization, refresh tokens
 * rotate with reuse detection killing the whole family, and logout blacklists the access token.
 */
@ActiveProfiles({"test", "e2e"})
class AuthFlowE2eIT extends BaseIntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired AuthzManagementService authz;
    @Autowired PasswordHasher hasher;

    boolean seeded;

    @BeforeEach
    void seed() {
        if (seeded) {
            return;
        }
        UUID userId = authz.createUser("e2e-alice", "alice@e2e.io", "Alice",
                hasher.hash("s3cret"), true);
        authz.createRole("e2e-viewer", "Viewer", null, Set.of("issue.read"));
        authz.grantRole(Subject.user(userId.toString()), "e2e-viewer",
                Scope.of("project", "p1"));
        seeded = true;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> login() {
        ResponseEntity<Map> response = rest.postForEntity("/auth/login",
                Map.of("username", "e2e-alice", "password", "s3cret"), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private ResponseEntity<String> getWithToken(String path, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    @Test
    void fullAuthAndAuthzFlow() {
        Map<String, Object> tokens = login();
        String access = (String) tokens.get("accessToken");
        String refresh = (String) tokens.get("refreshToken");

        // 1. Quyền per-project: p1 được cấp, p2 không
        assertThat(getWithToken("/api/projects/p1/issues", access).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(getWithToken("/api/projects/p2/issues", access).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // 2. Không token → 401
        assertThat(rest.getForEntity("/api/projects/p1/issues", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        // 3. Refresh rotation
        ResponseEntity<Map> refreshed = rest.postForEntity("/auth/refresh",
                Map.of("refreshToken", refresh), Map.class);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        String newRefresh = (String) refreshed.getBody().get("refreshToken");
        assertThat(newRefresh).isNotEqualTo(refresh);

        // 4. Reuse detection: refresh cũ → 401, và cả family chết → refresh mới cũng 401
        assertThat(rest.postForEntity("/auth/refresh",
                        Map.of("refreshToken", refresh), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(rest.postForEntity("/auth/refresh",
                        Map.of("refreshToken", newRefresh), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        // 5. Logout blacklist access token (jti lấy từ login mới)
        Map<String, Object> tokens2 = login();
        String access2 = (String) tokens2.get("accessToken");
        String refresh2 = (String) tokens2.get("refreshToken");
        assertThat(getWithToken("/api/projects/p1/issues", access2).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        String jti = io.jsonwebtoken.Jwts.parser()
                .verifyWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        "e2e-secret-0123456789abcdef-0123456789abcdef".getBytes()))
                .build().parseSignedClaims(access2).getPayload().getId();
        ResponseEntity<Void> logout = rest.postForEntity("/auth/logout",
                Map.of("refreshToken", refresh2, "accessTokenId", jti), Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(getWithToken("/api/projects/p1/issues", access2).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
