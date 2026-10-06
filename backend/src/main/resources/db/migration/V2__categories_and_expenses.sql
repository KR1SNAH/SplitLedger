-- Amounts are shown in the user's preferred currency (ISO 4217 code).
ALTER TABLE users ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'USD';

CREATE TABLE categories (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    -- NULL owner = built-in category shared by every user.
    user_id    UUID        REFERENCES users (id) ON DELETE CASCADE,
    name       VARCHAR(50) NOT NULL,
    color      VARCHAR(7)  NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Names are unique (case-insensitively) per owner; built-ins share the all-zero "owner".
CREATE UNIQUE INDEX uq_categories_owner_name
    ON categories (COALESCE(user_id, '00000000-0000-0000-0000-000000000000'), lower(name));

INSERT INTO categories (name, color) VALUES
    ('Food & Dining', '#f97316'),
    ('Groceries',     '#22c55e'),
    ('Transport',     '#3b82f6'),
    ('Housing',       '#8b5cf6'),
    ('Utilities',     '#eab308'),
    ('Entertainment', '#ec4899'),
    ('Shopping',      '#06b6d4'),
    ('Health',        '#ef4444'),
    ('Travel',        '#14b8a6'),
    ('Education',     '#6366f1'),
    ('Other',         '#64748b');

CREATE TABLE expenses (
    id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- Deleting a custom category leaves its expenses uncategorized rather than blocking the delete.
    category_id  UUID          REFERENCES categories (id) ON DELETE SET NULL,
    description  VARCHAR(120)  NOT NULL,
    amount       NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    expense_date DATE          NOT NULL,
    notes        VARCHAR(500),
    version      BIGINT        NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_expenses_user_date ON expenses (user_id, expense_date DESC);
CREATE INDEX idx_expenses_category ON expenses (category_id);
