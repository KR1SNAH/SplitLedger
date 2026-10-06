CREATE TABLE groups (
    id                     UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name                   VARCHAR(80)  NOT NULL,
    description            VARCHAR(300),
    -- ISO 4217 code all of the group's expenses are recorded in.
    currency               VARCHAR(3)   NOT NULL,
    created_by             UUID         REFERENCES users (id) ON DELETE SET NULL,
    -- Shareable invite link: only the SHA-256 hash of the token is stored. NULL = no active link.
    invite_token_hash      VARCHAR(64)  UNIQUE,
    invite_expires_at      TIMESTAMPTZ,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK ((invite_token_hash IS NULL) = (invite_expires_at IS NULL))
);

CREATE TABLE group_members (
    id        UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id  UUID        NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    user_id   UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role      VARCHAR(20) NOT NULL CHECK (role IN ('OWNER', 'MEMBER')),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (group_id, user_id)
);

CREATE INDEX idx_group_members_user ON group_members (user_id);

-- Pending invites addressed to an email. The row is deleted once accepted, declined or cancelled.
-- The email may not belong to a registered user yet; it matches whoever signs up with it.
CREATE TABLE group_invites (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id   UUID         NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    email      VARCHAR(255) NOT NULL,
    invited_by UUID         REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (group_id, email)
);

CREATE INDEX idx_group_invites_email ON group_invites (email);
