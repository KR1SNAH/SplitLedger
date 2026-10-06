# SplitLedger

Track your personal spending and split shared bills with friends: who paid, who owes whom, and the fewest payments to settle up.

**Stack:** Spring Boot 4 (Java 25) · Spring Security (JWT) · PostgreSQL 17 · Flyway · React 19 + TypeScript · Tailwind CSS · TanStack Query · Docker

> Work in progress — see [PLAN.md](PLAN.md) for the roadmap.

## Run it

### Everything in Docker

```bash
docker compose up --build
```

| Service   | URL                                   |
|-----------|---------------------------------------|
| App       | http://localhost:3000                 |
| API       | http://localhost:8080                 |
| API docs  | http://localhost:8080/swagger-ui.html |

A default admin account is seeded: `admin@splitledger.dev` / `admin12345` (override with `ADMIN_EMAIL` / `ADMIN_PASSWORD`, see [.env.example](.env.example)).

### Local development

```bash
docker compose up -d db              # Postgres only

cd backend && ./mvnw spring-boot:run # API on :8080
cd frontend && npm install && npm run dev   # UI on :5173, proxies /api to :8080
```

### Production

[docker-compose.prod.yml](docker-compose.prod.yml) runs the same images behind Caddy (automatic HTTPS), with no public ports on the API or database and with required secrets. See [deploy/DEPLOY.md](deploy/DEPLOY.md) for a step-by-step guide to hosting it for free on an Oracle Cloud Always Free VM.

### Tests

```bash
cd backend && ./mvnw test   # requires Docker (Testcontainers spins up Postgres)
```

## Design decisions

### Authentication
- **Short-lived access token (JWT, 15 min)** returned in the response body and kept only in memory on the client, so it never touches `localStorage`, where an XSS payload could read it.
- **Long-lived refresh token (14 days)** is an opaque random value in an `HttpOnly`, `SameSite=Strict` cookie scoped to `/api/auth`. The database stores only its SHA-256 hash.
- **Rotation with reuse detection:** every refresh revokes the old token and issues a new one. If a revoked token is ever presented again, it has likely been stolen, so all of that user's sessions are revoked.
- The frontend and API are served from the **same origin** (nginx proxy in Docker, Vite proxy in dev), which is what lets the strict cookie work without CORS.
- JWTs are validated by Spring Security's OAuth2 resource server support rather than a hand-written filter.

### Authorization
- App-level roles: `USER` and `ADMIN` (JWT `roles` claim → `ROLE_*` authorities). `/api/admin/**` requires `ADMIN`.
- **Group-level roles** (`OWNER` / `MEMBER`) are checked per request by a `GroupPermissions` bean used in `@PreAuthorize`, e.g. `@PreAuthorize("@groupPermissions.isOwner(#groupId, authentication)")`. Any member can view a group, and only owners can edit or delete it, manage invites, remove members or change roles.
- **Non-members get 404, members get 403.** Someone outside a group can't tell it exists. A member trying an owner-only action is told they lack permission.
- **A group always has an owner.** The last owner can't leave or step down. Membership changes take a `SELECT … FOR UPDATE` lock on the group row first, so two owners stepping down at the same moment can't both succeed. A concurrency test checks this, and it fails if the lock is removed.

### Invites
- **Invite links** are 256-bit random tokens. Like refresh tokens, only their SHA-256 hash is stored, so the link is shown once when created. A link expires after 7 days, and creating a new link or disabling it invalidates the old one. The token is sent in the request body, not the URL, so it doesn't end up in API access logs.
- **Email invites** are addressed to an email address and appear in-app for whoever signs in with it. Inviting an unregistered address gets the same response as inviting a registered one, so invites can't be used to find out who has an account.
- Limitation: emails aren't verified yet, so an invite to an unregistered address can be claimed by whoever registers that address first.

### Money
- Amounts are `NUMERIC(12,2)` in Postgres and `BigDecimal` in Java, never floating point. Requests with more than 2 decimal places are rejected.
- The frontend validates what the user typed as a string and never does money arithmetic itself; totals come from the database. While you fill in a split, the form asks a **preview endpoint** for the exact shares, so what you see is exactly what will be saved.

### Splitting expenses
- A group expense has one payer and any set of members to split it between: **equally**, by **exact amounts**, by **percentage** or by **shares** (e.g. 2 shares for a couple).
- The split engine (`com.splitledger.split`) is plain Java with no Spring, so it's unit-tested directly. It works in whole cents with exact integer arithmetic.
- **Leftover cents** use the *largest remainder method*: everyone gets their exact share rounded down, then the people who lost the most to rounding get one cent each, with ties going to whoever is listed first. So 100.00 split 3 ways is 33.34 / 33.33 / 33.33. The shares always add up to the total, nobody is more than a cent from their exact share, and the same input always gives the same result. Randomized tests check all three properties over thousands of generated splits.
- Invalid splits are rejected with a message the UI shows as-is, e.g. "The percentages add up to 90, not 100".

### Balances and settling up
- **Balances aren't stored.** Each member's balance (paid − owed + payments sent − payments received) is computed from the expenses and payments with a few grouped `SUM` queries, so it can never drift out of sync with them.
- **Debt simplification:** the app suggests the fewest payments that settle everyone up. It uses greedy minimum cash flow: the person who owes the most pays the person owed the most, repeatedly. That never needs more than n − 1 payments for n people. (The true minimum is NP-hard to find; greedy is the standard practical choice.)
- **Payments** ("Kim paid Demo 56.08") can be recorded by either person involved or by a group owner, so a member can't invent payments between two other people.
- **Only current members can have a non-zero balance.** You can't leave, or be removed, while you owe or are owed money. Editing or deleting old expenses and payments can't reopen a balance for someone who has already left. The group's currency is locked once it has any activity, since amounts are never converted.
- **Concurrency:** every write to a group's expenses or payments takes the same `SELECT … FOR UPDATE` lock on the group row as membership changes. A test races "Bob leaves" against "add an expense that includes Bob" and checks that both can never succeed. Without the lock, it fails.
- Expenses use optimistic locking like personal ones. Because shares live in their own table, the service explicitly touches the expense row on every edit, so changing only the split still bumps the version.
- Only an expense's creator or a group owner can edit or delete it.

### Insights and budgets
- **Personal and group spending are kept separate.** The dashboard shows your personal spending (by category and a 6-month trend) next to what you owe and are owed across all your groups. Group expenses have no category and may be in a different currency than yours, so mixing them in would need assumptions the app doesn't make.
- **No currency conversion, so totals are per currency.** "Across your groups" gives one line per currency (e.g. €45.00 owed to you, $5.00 you owe) and never adds EUR to USD. The cross-group summary takes four grouped `SUM` queries in total, however many groups you're in.
- **Budgets are recurring monthly limits**, one per category plus an optional overall budget, so you set them once. Spending isn't stored on the budget: it comes from the same per-category summary as the expenses page, computed for whichever month you're viewing. The database enforces "one budget per category" with a Postgres 15+ `UNIQUE NULLS NOT DISTINCT (user_id, category_id)` constraint, so the overall budget (whose category is `NULL`) can't be duplicated either.
- The server computes `remaining`, `percentUsed` and `overBudget`; the browser still does no money arithmetic. Over-budget and nearly-spent states are always shown in words as well as color.
- **Charts are hand-written** (plain HTML and Tailwind, no chart library), which keeps the bundle small. Each column is keyboard-focusable, shows a tooltip on hover or focus, and is labelled for screen readers.

### Admin
- **Disabling an account takes effect immediately, not when the access token expires.** It revokes the user's refresh tokens, login and refresh refuse disabled accounts, and a custom `OAuth2TokenValidator` in the JWT decoder checks the account on every request. That costs one primary-key lookup per request, so the JWTs are no longer purely stateless. The trade is deliberate: without the check, a disabled user would keep access for up to 15 minutes. A test proves the current token is rejected, and it fails if the validator is removed.
- Admins can't disable their own account, so there's always an admin left who can undo a mistake.
- **Platform stats are written in plain SQL** (`JdbcClient`), because they're aggregates over whole tables, not entity loading. They're bucketed by month in UTC and contain only counts, never money totals across currencies. "Active users" means someone who signed in or reopened the app (a session was issued or refreshed) in the last 30 days.

### Frontend performance
- Every page is its own lazily loaded chunk (React Router's route `lazy`), so the first load only downloads the app shell and the current page.

### Data access
- Every personal expense query is scoped to the signed-in user. Other users' records return **404, not 403**, so their existence isn't revealed.
- Expenses use **optimistic locking** (`@Version`): an update must include the version it was based on, so two tabs editing the same expense can't silently overwrite each other.
- List filters are built with the JPA Criteria API. Sort keys are whitelisted, and `%`/`_` in search text are escaped.

### API conventions
- Errors follow [RFC 9457 problem details](https://www.rfc-editor.org/rfc/rfc9457); validation errors include an `errors` map of field → message, which the UI maps onto form fields.
- Schema is owned by Flyway migrations; Hibernate only validates it.
