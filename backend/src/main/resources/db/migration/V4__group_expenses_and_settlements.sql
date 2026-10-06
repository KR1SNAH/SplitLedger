-- Shared expenses in a group. Amounts are in the group's currency.
CREATE TABLE group_expenses (
    id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id     UUID          NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    paid_by      UUID          NOT NULL REFERENCES users (id),
    description  VARCHAR(120)  NOT NULL,
    amount       NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    expense_date DATE          NOT NULL,
    split_type   VARCHAR(10)   NOT NULL CHECK (split_type IN ('EQUAL', 'EXACT', 'PERCENT', 'SHARES')),
    created_by   UUID          REFERENCES users (id) ON DELETE SET NULL,
    version      BIGINT        NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_group_expenses_group_date ON group_expenses (group_id, expense_date DESC, created_at DESC);

-- What each participant owes for an expense. The owed amounts always add up to the expense amount;
-- the application computes them (see SplitCalculator) so the leftover cents are allocated deterministically.
CREATE TABLE group_expense_shares (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    expense_id  UUID          NOT NULL REFERENCES group_expenses (id) ON DELETE CASCADE,
    user_id     UUID          NOT NULL REFERENCES users (id),
    -- Participant order: it decides who gets leftover cents on ties, and how the split is shown.
    sort_order  INT           NOT NULL,
    -- What was entered for this person: the exact amount, the percentage or the number of shares.
    -- NULL for EQUAL splits. Kept so the split can be shown and edited the way it was entered.
    share_input NUMERIC(12,2) CHECK (share_input > 0),
    owed        NUMERIC(12,2) NOT NULL CHECK (owed >= 0),
    UNIQUE (expense_id, user_id)
);

CREATE INDEX idx_group_expense_shares_user ON group_expense_shares (user_id);

-- A payment between two members that settles (part of) a debt: from_user paid to_user.
CREATE TABLE settlements (
    id         UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id   UUID          NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    from_user  UUID          NOT NULL REFERENCES users (id),
    to_user    UUID          NOT NULL REFERENCES users (id),
    amount     NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    settled_on DATE          NOT NULL,
    note       VARCHAR(200),
    created_by UUID          REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (from_user <> to_user)
);

CREATE INDEX idx_settlements_group_date ON settlements (group_id, settled_on DESC, created_at DESC);
