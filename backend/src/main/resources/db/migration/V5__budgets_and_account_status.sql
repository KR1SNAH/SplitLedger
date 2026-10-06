-- Admins can disable an account: it can't sign in, and its existing tokens stop working.
ALTER TABLE users ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- A recurring monthly spending limit: it applies to every month until changed.
-- NULL category = the overall budget for all personal spending.
CREATE TABLE budgets (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- Deleting a custom category deletes its budget: there's nothing left to spend against.
    category_id UUID          REFERENCES categories (id) ON DELETE CASCADE,
    amount      NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- One budget per category, and one overall budget (NULLS NOT DISTINCT: two NULLs collide).
    UNIQUE NULLS NOT DISTINCT (user_id, category_id)
);

-- The cross-group balance summary looks up everything one user paid, sent or received.
CREATE INDEX idx_group_expenses_paid_by ON group_expenses (paid_by);
CREATE INDEX idx_settlements_from_user ON settlements (from_user);
CREATE INDEX idx_settlements_to_user ON settlements (to_user);
