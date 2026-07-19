package io.javalibs.security.issuer;

import io.javalibs.security.TokenBlacklist;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Username/password authentication with rotating refresh tokens and reuse detection.
 * Wire the two stores from javalibs-authz-jpa (default) or your own implementation.
 *
 * <p>Refresh tokens rotate on every use (see {@link #refresh(String)}): each successful
 * refresh revokes the presented token and issues a new one in the same family. If a
 * revoked, already-rotated token is presented again — a strong signal the token was
 * stolen and used out of order — the entire family is revoked, forcing the legitimate
 * user to log in again.</p>
 */
public class AuthenticationService {

    /** Dummy BCrypt hash used to equalize timing when the username is unknown. */
    private static final String TIMING_NOISE_HASH =
            "{bcrypt}$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5B0m0FGz0dV7pJcyDgVOMZz2bhZTa";

    private final CredentialsStore credentialsStore;
    private final RefreshTokenStore refreshTokenStore;
    private final PasswordHasher passwordHasher;
    private final TokenIssuer tokenIssuer;
    private final JwtIssuerConfig config;
    private final Clock clock;
    private final TokenBlacklist tokenBlacklist; // nullable

    /**
     * Creates a new authentication service.
     *
     * @param credentialsStore the SPI for reading stored user credentials (must not be null)
     * @param refreshTokenStore the SPI for persisting refresh token state (must not be null)
     * @param passwordHasher verifies raw passwords against stored hashes (must not be null)
     * @param tokenIssuer signs access tokens (must not be null)
     * @param config the issuer configuration, used for access/refresh token TTLs (must not
     *     be null)
     * @param clock the clock used for all timestamps and TTL computations (must not be null)
     * @param tokenBlacklist optional access-token blacklist; when null, {@link #logout}
     *     revokes only the refresh token and cannot invalidate an already-issued access
     *     token before its natural expiry
     */
    public AuthenticationService(CredentialsStore credentialsStore,
            RefreshTokenStore refreshTokenStore, PasswordHasher passwordHasher,
            TokenIssuer tokenIssuer, JwtIssuerConfig config, Clock clock,
            TokenBlacklist tokenBlacklist) {
        this.credentialsStore = Objects.requireNonNull(credentialsStore);
        this.refreshTokenStore = Objects.requireNonNull(refreshTokenStore);
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
        this.tokenIssuer = Objects.requireNonNull(tokenIssuer);
        this.config = Objects.requireNonNull(config);
        this.clock = Objects.requireNonNull(clock);
        this.tokenBlacklist = tokenBlacklist;
    }

    /**
     * Authenticates and issues a fresh access + refresh token pair (a new token family).
     *
     * <p>Fails with a generic {@link InvalidCredentialsException} ("Invalid credentials")
     * for an unknown username, a wrong password, or a disabled account, so callers cannot
     * use the error to enumerate valid usernames. When the username is unknown, a dummy
     * password hash is still checked to keep the response time close to the case where the
     * password simply did not match.</p>
     *
     * @param username the login username
     * @param rawPassword the plaintext password to verify
     * @return a freshly issued access + refresh token pair
     * @throws InvalidCredentialsException when the credentials are invalid or the account
     *     is disabled
     */
    public TokenPair login(String username, String rawPassword) {
        Optional<StoredCredentials> found = credentialsStore.findByUsername(username);
        if (found.isEmpty()) {
            passwordHasher.matches(rawPassword, TIMING_NOISE_HASH);
            throw new InvalidCredentialsException("Invalid credentials");
        }
        StoredCredentials user = found.get();
        if (!passwordHasher.matches(rawPassword, user.passwordHash()) || !user.enabled()) {
            throw new InvalidCredentialsException("Invalid credentials");
        }
        return issuePair(user, UUID.randomUUID().toString());
    }

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

    /**
     * Rotates the refresh token, revoking the whole family when reuse is detected.
     *
     * <p>A token that is unknown or expired is rejected outright. A token that is revoked
     * but was never rotated (e.g. it was revoked by {@link #logout}) is also rejected but
     * does not trigger reuse detection. A token that is revoked <em>because</em> it was
     * already rotated indicates the presented token was reused out of order — a signal of
     * theft — so the entire family is revoked via
     * {@link RefreshTokenStore#revokeFamily(String, Instant)} before rejecting the call.</p>
     *
     * <p>Rotation itself is a race: two concurrent calls could both pass the checks above for
     * the same token before either commits. {@link RefreshTokenStore#markRotated(String,
     * String, Instant)} is required to be an atomic conditional update, so only one of the two
     * concurrent calls can win. The loser is treated exactly like reuse of an already-rotated
     * token — the whole family is revoked and the call is rejected — even though the record
     * looked un-revoked at the start of this call.</p>
     *
     * @param refreshToken the raw refresh token presented by the client
     * @return a freshly issued access + refresh token pair in the same family
     * @throws InvalidRefreshTokenException when the token is unknown, expired, revoked, or
     *     the owning account is disabled
     */
    public TokenPair refresh(String refreshToken) {
        Instant now = clock.instant();
        String hash = RefreshTokens.hash(refreshToken);
        RefreshTokenRecord record = refreshTokenStore.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token is invalid"));
        if (record.isRevoked()) {
            if (record.wasRotated()) {
                // Reuse of an already-rotated token: assume theft, kill the whole session family.
                refreshTokenStore.revokeFamily(record.familyId(), now);
            }
            throw new InvalidRefreshTokenException("Refresh token is invalid");
        }
        if (record.isExpired(now)) {
            throw new InvalidRefreshTokenException("Refresh token has expired");
        }
        StoredCredentials byId = credentialsStore.findByUserId(record.userId())
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token is invalid"));
        if (!byId.enabled()) {
            throw new InvalidRefreshTokenException("Refresh token is invalid");
        }
        String newToken = RefreshTokens.generate();
        boolean wonRotation = refreshTokenStore.markRotated(hash, RefreshTokens.hash(newToken), now);
        if (!wonRotation) {
            // Lost the race to a concurrent rotation of the same token: treat as reuse/theft.
            refreshTokenStore.revokeFamily(record.familyId(), now);
            throw new InvalidRefreshTokenException("Refresh token is invalid");
        }
        return issuePairWithRefreshToken(byId, record.familyId(), newToken);
    }

    /**
     * Revokes the refresh token and, when a blacklist is configured, the access token id.
     *
     * <p>Revoking an unknown or already-revoked refresh token is not an error: logout is
     * idempotent. When {@code accessTokenId} (the access token's {@code jti} claim) is
     * provided and a {@link TokenBlacklist} was configured, the access token is also
     * blacklisted until its natural expiry so it cannot be used again even though JWTs are
     * otherwise stateless.</p>
     *
     * @param refreshToken the raw refresh token to revoke; ignored when null or blank
     * @param accessTokenId the access token's {@code jti} claim to blacklist; ignored when
     *     null, blank, or no blacklist was configured
     */
    public void logout(String refreshToken, String accessTokenId) {
        Instant now = clock.instant();
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenStore.revoke(RefreshTokens.hash(refreshToken), now);
        }
        if (tokenBlacklist != null && accessTokenId != null && !accessTokenId.isBlank()) {
            tokenBlacklist.revoke(accessTokenId, now.plus(config.accessTokenTtl()));
        }
    }

    private TokenPair issuePair(StoredCredentials user, String familyId) {
        return issuePairWithRefreshToken(user, familyId, RefreshTokens.generate());
    }

    private TokenPair issuePairWithRefreshToken(StoredCredentials user, String familyId,
            String refreshToken) {
        TokenIssuer.IssuedToken access = tokenIssuer.issue(
                user.userId(), user.username(), user.email(), user.roles(), user.extraClaims());
        Instant refreshExpiresAt = clock.instant().plus(config.refreshTokenTtl());
        refreshTokenStore.save(new RefreshTokenRecord(RefreshTokens.hash(refreshToken),
                user.userId(), familyId, refreshExpiresAt, null, null));
        return new TokenPair(access.token(), access.expiresAt(), refreshToken, refreshExpiresAt);
    }
}
