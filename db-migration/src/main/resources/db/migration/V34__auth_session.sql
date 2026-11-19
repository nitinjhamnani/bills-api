CREATE TABLE auth_session (
    session_id        uuid PRIMARY KEY,
    user_id           varchar(6)   NOT NULL REFERENCES app_user (user_id),
    portal            varchar(16)  NOT NULL,
    user_type         varchar(32)  NOT NULL,
    role_name         varchar(32)  NOT NULL,
    client_id         varchar(64),
    refresh_hash      varchar(128) NOT NULL,
    prev_refresh_hash varchar(128),
    created_on        timestamptz  NOT NULL,
    last_seen_on      timestamptz  NOT NULL,
    expires_on        timestamptz  NOT NULL,
    revoked_on        timestamptz,
    revoked_reason    varchar(64),
    user_agent        varchar(300),
    ip_address        varchar(64)
);

CREATE INDEX idx_auth_session_active_user ON auth_session (user_id) WHERE revoked_on IS NULL;
CREATE INDEX idx_auth_session_refresh_hash ON auth_session (refresh_hash);
CREATE INDEX idx_auth_session_prev_refresh_hash ON auth_session (prev_refresh_hash) WHERE prev_refresh_hash IS NOT NULL;
