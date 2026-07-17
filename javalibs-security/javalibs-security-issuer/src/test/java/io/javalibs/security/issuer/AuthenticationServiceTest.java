package io.javalibs.security.issuer;

import io.javalibs.security.InMemoryTokenBlacklist;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class AuthenticationServiceTest {

    static class InMemoryRefreshTokenStore implements RefreshTokenStore {
        final Map<String, RefreshTokenRecord> byHash = new HashMap<>();

        @Override public void save(RefreshTokenRecord record) {
            byHash.put(record.tokenHash(), record);
        }
        @Override public Optional<RefreshTokenRecord> findByTokenHash(String tokenHash) {
            return Optional.ofNullable(byHash.get(tokenHash));
        }
        @Override public void markRotated(String tokenHash, String rotatedToHash, Instant revokedAt) {
            RefreshTokenRecord r = byHash.get(tokenHash);
            byHash.put(tokenHash, new RefreshTokenRecord(r.tokenHash(), r.userId(),
                    r.familyId(), r.expiresAt(), revokedAt, rotatedToHash));
        }
        @Override public void revoke(String tokenHash, Instant revokedAt) {
            RefreshTokenRecord r = byHash.get(tokenHash);
            if (r != null) {
                byHash.put(tokenHash, new RefreshTokenRecord(r.tokenHash(), r.userId(),
                        r.familyId(), r.expiresAt(), revokedAt, r.rotatedToHash()));
            }
        }
        @Override public void revokeFamily(String familyId, Instant revokedAt) {
            byHash.replaceAll((hash, r) -> r.familyId().equals(familyId) && r.revokedAt() == null
                    ? new RefreshTokenRecord(r.tokenHash(), r.userId(), r.familyId(),
                            r.expiresAt(), revokedAt, r.rotatedToHash())
                    : r);
        }
    }

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    PasswordHasher hasher = new PasswordHasher();
    InMemoryRefreshTokenStore refreshTokens = new InMemoryRefreshTokenStore();
    InMemoryTokenBlacklist blacklist = new InMemoryTokenBlacklist();
    CredentialsStore credentials;
    AuthenticationService service;

    @BeforeEach
    void setUp() {
        StoredCredentials alice = new StoredCredentials(
                "u1", "alice", "alice@example.com", hasher.hash("s3cret"), true);
        StoredCredentials disabled = new StoredCredentials(
                "u2", "bob", null, hasher.hash("s3cret"), false);
        Map<String, StoredCredentials> byUsername = Map.of("alice", alice, "bob", disabled);
        Map<String, StoredCredentials> byUserId = Map.of("u1", alice, "u2", disabled);
        credentials = new CredentialsStore() {
            @Override public Optional<StoredCredentials> findByUsername(String username) {
                return Optional.ofNullable(byUsername.get(username));
            }
            @Override public Optional<StoredCredentials> findByUserId(String userId) {
                return Optional.ofNullable(byUserId.get(userId));
            }
        };
        JwtIssuerConfig config = JwtIssuerConfig.builder().hmacSecret(SECRET).build();
        service = new AuthenticationService(credentials, refreshTokens, hasher,
                new TokenIssuer(config, Clock.systemUTC()), config, Clock.systemUTC(), blacklist);
    }

    @Test
    void loginIssuesTokenPairAndStoresHashedRefreshToken() {
        TokenPair pair = service.login("alice", "s3cret");
        assertThat(pair.accessToken()).isNotBlank();
        assertThat(refreshTokens.byHash)
                .containsKey(RefreshTokens.hash(pair.refreshToken()))
                .doesNotContainKey(pair.refreshToken());
    }

    @Test
    void loginRejectsWrongPasswordUnknownUserAndDisabledUser() {
        assertThatExceptionOfType(InvalidCredentialsException.class)
                .isThrownBy(() -> service.login("alice", "wrong"))
                .withMessage("Invalid credentials");
        assertThatExceptionOfType(InvalidCredentialsException.class)
                .isThrownBy(() -> service.login("nobody", "s3cret"))
                .withMessage("Invalid credentials");
        assertThatExceptionOfType(InvalidCredentialsException.class)
                .isThrownBy(() -> service.login("bob", "s3cret"))
                .withMessage("Invalid credentials");
    }

    @Test
    void refreshRotatesToken() {
        TokenPair first = service.login("alice", "s3cret");
        TokenPair second = service.refresh(first.refreshToken());
        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        RefreshTokenRecord old = refreshTokens.byHash
                .get(RefreshTokens.hash(first.refreshToken()));
        assertThat(old.revokedAt()).isNotNull();
        assertThat(old.rotatedToHash())
                .isEqualTo(RefreshTokens.hash(second.refreshToken()));
    }

    @Test
    void reusingRotatedTokenRevokesWholeFamily() {
        TokenPair first = service.login("alice", "s3cret");
        TokenPair second = service.refresh(first.refreshToken());
        assertThatExceptionOfType(InvalidRefreshTokenException.class)
                .isThrownBy(() -> service.refresh(first.refreshToken()));
        // token mới cùng family cũng đã bị revoke
        assertThatExceptionOfType(InvalidRefreshTokenException.class)
                .isThrownBy(() -> service.refresh(second.refreshToken()));
    }

    @Test
    void unknownRefreshTokenIsRejected() {
        assertThatExceptionOfType(InvalidRefreshTokenException.class)
                .isThrownBy(() -> service.refresh(RefreshTokens.generate()));
    }

    @Test
    void logoutRevokesRefreshTokenAndBlacklistsAccessJti() {
        TokenPair pair = service.login("alice", "s3cret");
        service.logout(pair.refreshToken(), "jti-123");
        assertThat(blacklist.isRevoked("jti-123")).isTrue();
        assertThatExceptionOfType(InvalidRefreshTokenException.class)
                .isThrownBy(() -> service.refresh(pair.refreshToken()));
    }
}
