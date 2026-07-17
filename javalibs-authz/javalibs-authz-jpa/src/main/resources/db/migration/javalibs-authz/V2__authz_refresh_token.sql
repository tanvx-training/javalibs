CREATE TABLE authz_refresh_token (
    id              UUID PRIMARY KEY,
    user_id         UUID        NOT NULL REFERENCES authz_user (id) ON DELETE CASCADE,
    token_hash      VARCHAR(64) NOT NULL UNIQUE,
    family_id       VARCHAR(36) NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ,
    rotated_to_hash VARCHAR(64),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_authz_refresh_token_family ON authz_refresh_token (family_id);
