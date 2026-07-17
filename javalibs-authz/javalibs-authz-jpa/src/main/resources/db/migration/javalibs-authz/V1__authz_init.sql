CREATE TABLE authz_user (
    id            UUID PRIMARY KEY,
    username      VARCHAR(150) NOT NULL UNIQUE,
    email         VARCHAR(320),
    password_hash VARCHAR(200) NOT NULL,
    display_name  VARCHAR(200),
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE authz_group (
    id          UUID PRIMARY KEY,
    name        VARCHAR(150) NOT NULL UNIQUE,
    description VARCHAR(500)
);

CREATE TABLE authz_group_member (
    id       UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES authz_group (id) ON DELETE CASCADE,
    user_id  UUID NOT NULL REFERENCES authz_user (id) ON DELETE CASCADE,
    CONSTRAINT uq_authz_group_member UNIQUE (group_id, user_id)
);
CREATE INDEX idx_authz_group_member_user ON authz_group_member (user_id);

CREATE TABLE authz_role (
    id          UUID PRIMARY KEY,
    role_key    VARCHAR(150) NOT NULL UNIQUE,
    name        VARCHAR(200) NOT NULL,
    description VARCHAR(500)
);

CREATE TABLE authz_role_permission (
    role_id    UUID         NOT NULL REFERENCES authz_role (id) ON DELETE CASCADE,
    permission VARCHAR(150) NOT NULL,
    PRIMARY KEY (role_id, permission)
);

-- scope_type/scope_id = '' (chuỗi rỗng) nghĩa là GLOBAL; tránh NULL để UNIQUE hoạt động
CREATE TABLE authz_role_grant (
    id           UUID PRIMARY KEY,
    subject_type VARCHAR(10)  NOT NULL,
    subject_id   VARCHAR(64)  NOT NULL,
    role_id      UUID         NOT NULL REFERENCES authz_role (id) ON DELETE CASCADE,
    scope_type   VARCHAR(100) NOT NULL DEFAULT '',
    scope_id     VARCHAR(100) NOT NULL DEFAULT '',
    CONSTRAINT chk_authz_grant_scope CHECK ((scope_type = '') = (scope_id = '')),
    CONSTRAINT uq_authz_role_grant UNIQUE (subject_type, subject_id, role_id, scope_type, scope_id)
);
CREATE INDEX idx_authz_role_grant_subject ON authz_role_grant (subject_type, subject_id);
