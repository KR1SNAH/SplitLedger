# SplitLedger: Handoff

_Last updated: 2026-09-24. Phases 0–5 done; Phase 6 (CI, deploy, README diagrams) is next._

Read this first, then [PLAN.md](PLAN.md) (roadmap with checkboxes) and [README.md](README.md) (how to run it and the design decisions, written for recruiters).

---

## 1. What this is

**SplitLedger** is the developer's main **portfolio showcase project**: a personal expense tracker combined with a bill splitter (Splitwise-style). The goal is to show a production-style full stack with real business logic, not just CRUD.

Requirements the project must meet (from the developer's brief):
- React frontend + Spring Boot REST API + PostgreSQL
- JWT auth with role-based access control (admin vs user)
- Frontend and backend both Dockerized, run with docker-compose

The developer chose **Java backend + React TypeScript frontend**. An older client-only JSX calculator exists at `../Bill Splitter/`. **Ignore it**; the developer explicitly asked for a fresh UI.

"SplitLedger" is a placeholder name (package `com.splitledger`). Renaming is fine if the developer asks.

---

## 2. Current status

| Phase | Status |
|---|---|
| 0. Foundation (monorepo, Docker, tooling) | ✅ Done |
| 1. Auth & RBAC | ✅ Done |
| 2. Personal expenses + categories + dashboard | ✅ Done |
| 3. Groups (OWNER/MEMBER, invites) | ✅ Done |
| 4. Split engine (the core business logic) | ✅ Done |
| 5. Insights, budgets, admin stats | ✅ Done |
| **6. CI, deploy, README diagrams** | ⏭️ **Next** |

**Verified at handoff:**
- `./mvnw test`: **123/123 passing** (Testcontainers + Postgres; includes 32 pure unit tests for the split engine)
- `npm run build` and `npm run lint` in `frontend/`: clean, and no more chunk-size warning (route-level code splitting)
- Phase 5 API smoke-tested end to end through nginx after `docker compose up --build` (V5 migrated cleanly over the existing `pgdata` volume): budgets, monthly totals, `/api/balances`, admin stats, disable → old token 401 / refresh 401 / login 403 → enable → login 200.
- `docker compose up --build`: whole stack works. The Phase 4 API was smoke-tested end to end through nginx (split, preview, balances, suggested payments, settle up, leave rules, currency lock) against the existing `pgdata` volume, so V4 migrates cleanly over old data.

**Not yet done / open items:**
- **Nothing is committed.** `git init -b main` was run, but there are no commits. The developer was asked whether to make an initial commit and hasn't answered. Only commit when asked.
- The Phase 2–5 UI (Expenses, Settings, Dashboard, Budgets, Groups, Group detail with the split expense form, balances and settle up, Join page, Admin) was **never checked in a browser** (no browser tool was available). It compiles and lints, and the APIs behind it work, but a visual/manual pass is still owed. Check the split form, the dashboard (charts, tiles) and the Budgets page first.
- The Phase 5 smoke test left throwaway `smoke-…@example.com` users in the Docker `pgdata` volume (harmless).
- No GitHub remote and no CI yet.

---

## 3. Environment (developer's machine)

- Windows 11. Tools: **Git Bash** (Bash tool) and **PowerShell 5.1**.
- Java 25 (Oracle), Maven 3.9.11 (the project uses `./mvnw`), Node 22.20, npm 11, Docker 29 with Compose v5.
- **Docker Desktop is often not running.** Start it with `Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe"`, then poll `docker info`. The tests need Docker because of Testcontainers.
- The developer has the **RTK** hook installed; it rewrites shell commands for token savings. Once, a large bash heredoc containing apostrophes failed to parse after the rewrite. **Use the Write tool for source files** rather than big heredocs.
- The Docker stack may still be running from the last session, with data in the `pgdata` volume.

### Accounts in the running stack
- Admin (seeded on startup): `admin@splitledger.dev` / `admin12345`
- Demo user: `demo@example.com` / `password123` (has 2 Sep-2026 expenses)

### Commands
```bash
# Full stack: app on :3000, API on :8080, Swagger at /swagger-ui.html
docker compose up -d --build

# Local dev
docker compose up -d db
cd backend && ./mvnw spring-boot:run
cd frontend && npm run dev            # :5173, proxies /api -> :8080

# Checks
cd backend && ./mvnw test
cd frontend && npm run build && npm run lint
```

---

## 4. Stack & versions

**Backend:** Spring Boot **4.1.1** (Spring Framework 7, Spring Security 7, Hibernate 7, **Jackson 3**), Java **25**, PostgreSQL **17** (`postgres:17-alpine`), Flyway, springdoc-openapi **3.1.1**, Lombok, Testcontainers.

**Frontend:** React **19.2**, TypeScript **6.0** (strict), Vite **8**, Tailwind CSS **4** (via `@tailwindcss/vite`, no config file), React Router **7** (`react-router` package, `createBrowserRouter`), TanStack Query **5**, React Hook Form **7** + Zod **4** + `@hookform/resolvers` 5, **oxlint** (not ESLint). No component library: small hand-written Tailwind components (shadcn/ui was considered and deferred).

**Infra:** multi-stage Dockerfiles (`eclipse-temurin:25-jdk` → `25-jre`, non-root; `node:22-alpine` → `nginx:1.29-alpine`). nginx serves the SPA and proxies `/api/` to `backend:8080`.

---

## 5. Repository layout

```
splitledger/
├── HANDOFF.md  PLAN.md  README.md  .env.example  .gitignore  .gitattributes  docker-compose.yml  docker-compose.prod.yml
├── deploy/                        # Caddyfile, backup.sh, DEPLOY.md (Oracle Cloud Always Free guide)
├── backend/                       # Spring Boot (Maven), package com.splitledger
│   ├── Dockerfile
│   └── src/main/java/com/splitledger/
│       ├── SplitLedgerApplication.java   (@ConfigurationPropertiesScan)
│       ├── config/     SecurityConfig, OpenApiConfig, AdminProperties, AdminSeeder
│       ├── common/     ApiException, GlobalExceptionHandler, PageResponse, CurrentUser
│       ├── auth/       AuthController/Service, AccessTokenService (JWT), EnabledAccountValidator, RefreshToken(+Repo, Service), AuthProperties, dto/
│       ├── user/       User, Role, UserRepository, UserService, UserController, UserResponse, UpdateProfileRequest
│       ├── admin/      AdminUserController/Service (list, enable/disable), AdminStatsController/Service (JdbcClient SQL), AdminStats
│       ├── category/   Category, CategoryRepository, CategoryService, CategoryController, Request/Response
│       ├── expense/    Expense, ExpenseRepository, ExpenseFilter, ExpenseService, ExpenseController, dto/ (incl. MonthTotal)
│       ├── budget/     Budget, BudgetRepository, BudgetService, BudgetController, dto/ (BudgetRequest, BudgetAmountRequest, BudgetStatus, BudgetResponse)
│       ├── group/      Group, GroupMember, GroupRole, repos, GroupPermissions (@PreAuthorize bean), GroupService, GroupController, dto/
│       │   └── invite/ GroupInvite(+Repo), InviteService (links + email), InviteController
│       ├── split/      PURE JAVA (no Spring): SplitType, SplitCalculator, DebtSimplifier, SplitException
│       └── ledger/     GroupExpense(+Share), Settlement, repos, UserAmount, BalanceService, GroupExpenseService,
│                       SettlementService, GroupExpenseController, SettlementController (also /balances), MyBalancesController (/api/balances), GroupAmount, dto/
│   └── src/main/resources/  application.yml, db/migration/V1__…sql … V5__budgets_and_account_status.sql
│   └── src/test/java/com/splitledger/
│       ├── IntegrationTest.java          # base class: MockMvc + Testcontainers + helpers
│       ├── TestcontainersConfiguration.java (public), TestSplitLedgerApplication.java
│       ├── admin/ auth/ budget/ category/ expense/ group/ group/invite/ user/ ledger/  *IntegrationTest.java
│       └── split/  SplitCalculatorTest, DebtSimplifierTest (plain JUnit, incl. randomized property tests)
└── frontend/                      # Vite + React TS
    ├── Dockerfile  nginx.conf  vite.config.ts (alias @ → src, /api proxy)
    └── src/
        ├── main.tsx (QueryClient, AuthProvider)   App.tsx (routes)
        ├── lib/        api.ts (fetch client + token/refresh), types.ts, format.ts, formErrors.ts, cn.ts
        ├── auth/       context.ts, AuthContext.tsx (provider), useAuth.ts, guards.tsx (RequireAuth, GuestOnly)
        ├── components/ AppLayout, AuthCard, Logo, CategoryBadge, ConfirmDialog, EmptyState, charts/ColumnChart
        │   └── ui/     Button, TextField, fields.tsx (SelectField, TextareaField), Modal, Alert, styles.ts (inputClass)
        ├── features/   categories/{api.ts, CategoryManager.tsx}, expenses/{api.ts, ExpenseFormDialog.tsx},
        │               budgets/{api.ts, BudgetMeter.tsx, BudgetFormDialog.tsx}, admin/api.ts,
        │               groups/{api.ts, GroupFormDialog.tsx, MemberList.tsx, InvitePanel.tsx},
        │               ledger/{api.ts, splitTypes.ts, GroupExpenseFormDialog.tsx, GroupExpenseList.tsx,
        │                       BalancesPanel.tsx, SettleUpDialog.tsx, SettlementList.tsx}
        ├── hooks/      useDebouncedValue.ts
        └── pages/      Login, Register, Dashboard, Expenses, Budgets, Settings, Groups, GroupDetail, JoinGroup, Admin, NotFound (all lazy-loaded in App.tsx)
```

---

## 6. Architecture & key decisions (keep these consistent)

### Auth
- **Access token:** a 15-minute HS256 JWT, issued with `NimbusJwtEncoder` and validated by Spring's **OAuth2 resource server** (no custom JWT filter). Claims: `sub` = user UUID, `email`, `name`, `roles` (e.g. `["ADMIN"]`), mapped to `ROLE_*`. The frontend keeps it **in memory only**.
- **Refresh token:** opaque, 32 random bytes (`common/SecureTokens`, shared with invite links), stored as a **SHA-256 hash** in `refresh_tokens`. Sent as the `sl_refresh` cookie: `HttpOnly`, `SameSite=Strict`, `Path=/api/auth`, 14 days.
- **Rotation + reuse detection:** every refresh revokes the old token and issues a new one. Presenting an already-revoked token revokes **all** of that user's tokens. `rotate()` uses `noRollbackFor = ApiException.class` so the mass revocation still commits.
- **CSRF is disabled.** The API only accepts bearer tokens from the header; the cookie endpoints rely on SameSite=Strict. This works because the SPA and API are **same-origin** (nginx in Docker, the Vite proxy in dev). There is no CORS config, and none is needed.
- **Frontend `refreshSession()` is single-flight.** Concurrent callers share one request, because parallel refreshes would trigger reuse detection and log the user out. React StrictMode double-mounts effects in dev, so this matters.
- On app load, `AuthProvider` calls refresh to restore the session. `api()` retries once after a 401 by refreshing. `GuestOnly` performs the post-login redirect, including the `state.from` return path, so the login and register pages don't call `navigate` themselves.
- **Disabled accounts (Phase 5):** `users.enabled`. Disabling revokes all refresh tokens; `login` → 403 (checked *after* the password, so it doesn't leak which accounts exist); `rotate` → 401; and `EnabledAccountValidator` (in the `JwtDecoder`'s `DelegatingOAuth2TokenValidator`) rejects the current access token on every request via `existsByIdAndEnabledTrue`, a deliberate one-PK-lookup-per-request trade-off. `AdminIntegrationTest.disablingSignsTheUserOutEverywhereImmediately` fails (200 instead of 401) if the validator is removed (verified). Admins can't disable themselves (400).
- Admin is seeded from `app.admin.*` (env `ADMIN_EMAIL`/`ADMIN_PASSWORD`). Public registration always creates `USER`.
- `application.yml` and `docker-compose.yml` contain a **dev-only default `JWT_SECRET`**. It must be overridden for any deployment.

### Authorization
- App-level: `/api/admin/**` → `hasRole("ADMIN")` in `SecurityConfig`.
- **Group-level (Phase 3):** controllers use `@PreAuthorize("@groupPermissions.isMember(#groupId, authentication)")` / `isOwner(...)`. The `GroupPermissions` bean **throws `ApiException.notFound`** for non-members (SpEL rethrows runtime exceptions as they are, so this becomes a 404), and returns `false` for a member who isn't an owner (→ `AccessDeniedException` → 403 ProblemDetail via `GlobalExceptionHandler`). Path variables must be named `groupId` for `#groupId` to resolve. **Reuse this for every Phase 4 group endpoint.**
- Group invariants live in `GroupService`, not the permission bean. Membership changes call `groupService.lock(groupId)` (`SELECT … FOR UPDATE`) first. The "at least one owner" rule depends on this, and `GroupIntegrationTest.concurrentStepDownsKeepAnOwner` proves it: that test fails without the lock.
- Resource ownership: services load records **scoped to the current user** (e.g. `findByIdAndOwnerId`). Records owned by someone else return **404**, not 403.
- Controllers get the user with `@AuthenticationPrincipal Jwt jwt` → `CurrentUser.id(jwt)`.

### Money
- `NUMERIC(12,2)` ↔ `BigDecimal`; requests validated with `@DecimalMin("0.01") @Digits(integer=10, fraction=2)`. JSON carries amounts as numbers.
- The frontend validates the amount **as a string** (regex), converts it with `Number()` only on submit, and **never does money arithmetic**; totals come from the backend. **Phase 4 splitting math must happen on the backend** with `BigDecimal` and explicit remainder-cent allocation.
- Each user has a display `currency` (ISO 4217, default USD, validated with `java.util.Currency`). There is no multi-currency conversion; that's a stretch goal.

### Ledger (Phase 4)
- **Split engine** (`split/`) is plain Java: `SplitCalculator.split(type, total, values)` returns owed amounts in input order. Works in cents with `BigInteger`; leftover cents by **largest remainder**, ties → participant order. `DebtSimplifier.simplify(Map<K, BigDecimal>)` is greedy min cash flow, deterministic tie-break by key. Split errors throw `SplitException` → 400 via `GlobalExceptionHandler`, and the message is shown to users as-is.
- **Balances are derived, never stored** (`BalanceService`: 4 grouped SUM queries → `net = paid − owed + sent − received`).
- **Invariant: only current members can have a non-zero balance.** `GroupService.leave/removeMember` → 409 if the balance isn't zero. After any update/delete of an expense or settlement, `balanceService.requireFormerMembersSettled(groupId)` → 409 (rolls back) if a former member's balance became non-zero. On update, people who already left may stay on that expense (so old expenses stay editable), but new participants and payers must be current members.
- **Every ledger write calls `groupService.lock(groupId)` first**, like membership changes. `BalanceAndSettlementIntegrationTest.leavingCantRaceAnExpenseThatIncludesYou` fails with `[204, 201]` if the lock in `GroupExpenseService.create` is removed (verified).
- Permissions (developer's choices): an expense can be edited or deleted by **its creator or any owner** (403 otherwise; `canEdit` is in the response). A settlement can be recorded or deleted by **either party or an owner** (`canDelete`). **Single payer** per expense. Overpaying is allowed (the balance just flips).
- Group **currency is locked (409) once the group has expenses or settlements**.
- Shares are updated **in place, matched by user** (`replaceShares`), not cleared and re-inserted: Hibernate flushes inserts before deletes, which would violate `UNIQUE (expense_id, user_id)`.
- `GroupExpense.updatedAt` is set by the service (not `@UpdateTimestamp`) so that a shares-only edit still dirties the row and bumps `@Version`.
- `hibernate.default_batch_fetch_size: 50` in `application.yml` avoids N+1 when a page of expenses loads its shares (a paged query can't fetch-join a collection).
- `POST /expenses/preview` runs the exact same validation and split as saving. The form calls it (debounced) for the live per-person amounts; **no money math in the browser**.
- Frontend query keys: everything money-related is under `['groups', 'ledger', groupId, …]` (`ledgerKey`); any ledger mutation invalidates the whole prefix. Group updates, leave/remove and delete also refresh or drop it.

### Insights & budgets (Phase 5)
- Personal spending and group balances are **kept separate** (developer's choice). Insights and budgets cover personal expenses only; the dashboard shows group balances next to them.
- `GET /api/balances` (`BalanceService.myBalances`): 4 queries grouped by group for one user (`GroupAmount` constructor expressions), joined in memory with `findSummaries(userId)`. Totals are **per currency** (sorted), never converted. Groups the user left are excluded (their balance there is zero by the invariant).
- Budgets are **recurring** (one limit applies to every month). `category_id NULL` = overall. `UNIQUE NULLS NOT DISTINCT (user_id, category_id)`; the service pre-checks and also maps a racing `DataIntegrityViolationException` to 409. Deleting a category cascades to its budget. The category is fixed once created (PUT only changes `amount`).
- `BudgetService.statuses` reuses `ExpenseService.summarize` for the month; the server computes `remaining`, `percentUsed` (rounded down) and `overBudget` (strictly greater than the limit).
- Monthly totals: HQL `year()`/`month()` grouping, zero-filled in Java. `YearMonth` binds from `?month=2026-09` and serializes as `"2026-09"` out of the box (Jackson 3).
- Admin stats use `JdbcClient` plain SQL, bucketed in **UTC** with `to_char(created_at AT TIME ZONE 'UTC', 'YYYY-MM')`. Counts only (no cross-currency money). "Active" = has a refresh token created in the last 30 days.
- Frontend query keys: budgets are under `['expenses', 'budgets', month]` so expense and category mutations refresh them for free; monthly totals are `['expenses', 'monthly', …]`; `myBalancesKey = ['groups', 'my-balances']` is invalidated by every ledger mutation and by group join/leave/delete/update; admin data is under `['admin', …]`.
- Charts: `components/charts/ColumnChart` (plain HTML/Tailwind; single series; current period highlighted and labelled; a focusable `<li>` per column with `aria-label` and a hover/focus tooltip). Two measures → two charts, never dual axes. `BudgetMeter` colors: emerald < 80%, amber ≥ 80%, red over, always with a text label.
- Routes: every page is a React Router `lazy` route; the root route has `hydrateFallbackElement` (the exported `FullPageSpinner` from `auth/guards.tsx`). The admin page moved from `/admin/users` to `/admin`.

### Data & API conventions
- **Flyway owns the schema** and Hibernate runs `ddl-auto=validate`. New tables go in a new file: **next is `V6__…sql`**. Never edit applied migrations. Use `VARCHAR`, not `CHAR`, so Hibernate validation doesn't complain about type mismatches.
- UUID primary keys (`gen_random_uuid()` / `GenerationType.UUID`); `TIMESTAMPTZ` ↔ `Instant` with `@CreationTimestamp`/`@UpdateTimestamp`.
- `open-in-view: false`. Services are `@Transactional` and **return DTOs** (records with a static `from(entity)`), never entities.
- **Optimistic locking** on `Expense` (`@Version`). Updates must send `version`: a mismatch gives a 409 from the service, and a concurrent race gives `ObjectOptimisticLockingFailureException`, which becomes a 409 in `GlobalExceptionHandler`. Reuse this pattern for group expenses.
- **Errors:** RFC 9457 `ProblemDetail`. Throw `ApiException.notFound/conflict/forbidden/badRequest/unauthorized(msg)`. Validation errors include `errors: {field: message}`, which the frontend's `applyServerErrors()` maps onto form fields.
- **Pagination:** return `PageResponse<T>` (`content, page, size, totalElements, totalPages`), never Spring's `Page`. Sort keys are **whitelisted** (see `ExpenseService.SORTABLE`); the format is `sort=date,desc`.
- Filters are query-param records bound as model attributes (`ExpenseFilter`, with `@DateTimeFormat` and cross-field validation via `@AssertTrue`). Predicates are built with the JPA Criteria API, LIKE input is escaped, and `findAll(spec, pageable)` uses an `@EntityGraph` to avoid N+1 queries.
- Controllers have `@Tag` for Swagger.

### Frontend conventions
- Server state lives in **TanStack Query**. Each feature has `features/<name>/api.ts` with query hooks and mutation hooks. Query keys: `['expenses', 'list'|'summary', params]` and `['categories']`. Mutations invalidate `['expenses']`; category changes invalidate both. `retry` skips 4xx errors.
- Forms use **React Hook Form + Zod**, with Zod schemas that **mirror the backend constraints**. `ApiError` from `lib/api.ts` carries `status` and `fieldErrors`.
- List filters live in the **URL search params** (see `ExpensesPage`); search input is debounced.
- UI kit: `Button` (variants `primary|secondary|ghost|danger`; don't override `bg-*` with `className`, add a variant instead), `TextField`, `SelectField`, `TextareaField`, `Modal` (native `<dialog>`), `ConfirmDialog`, `EmptyState`, `CategoryBadge`, and `inputClass()` in `ui/styles.ts`. Style: slate neutrals, emerald accent, `rounded-2xl` cards with `border-slate-200`.
- oxlint's `only-export-components` rule: files exporting components must not also export constants or functions. That's why `auth/context.ts` and `ui/styles.ts` are separate files.
- Dates: use `toIsoDate`/`formatDate` in `lib/format.ts`, never `new Date('YYYY-MM-DD')`, which shifts across time zones. Money: `formatMoney(amount, user.currency)`.

---

## 7. API (current)

| Method | Path | Notes |
|---|---|---|
| POST | `/api/auth/register` `/login` | → `AuthResponse` + refresh cookie |
| POST | `/api/auth/refresh` `/logout` | cookie-based |
| GET/PUT | `/api/users/me` | profile (`displayName`, `currency`) |
| GET | `/api/admin/users?page&size` | ADMIN only |
| GET/POST | `/api/categories` | built-ins + own |
| PUT/DELETE | `/api/categories/{id}` | own only; built-ins → 403 |
| GET | `/api/expenses?from&to&categoryId&q&page&size&sort` | paginated |
| GET | `/api/expenses/summary?from&to&categoryId&q` | `{total, count, byCategory[]}` |
| GET/PUT/DELETE | `/api/expenses/{id}` | PUT requires `version` |
| POST | `/api/expenses` | |
| GET/POST | `/api/groups` | my groups (`GroupSummary[]` with `myRole`, `memberCount`) / create (creator = OWNER, currency defaults to theirs) |
| GET | `/api/groups/{groupId}` | member; `GroupDetail` (members owners-first; `inviteLink` only for owners) |
| PUT/DELETE | `/api/groups/{groupId}` | owner |
| POST | `/api/groups/{groupId}/leave` | member; last owner → 409 |
| DELETE | `/api/groups/{groupId}/members/{userId}` | owner; self → 400 |
| PUT | `/api/groups/{groupId}/members/{userId}/role` | owner; `{role}`; demoting the last owner → 409 |
| POST/DELETE | `/api/groups/{groupId}/invite-link` | owner; POST returns `{token, expiresAt}` once (replaces old link) |
| GET/POST | `/api/groups/{groupId}/invites` | owner; pending email invites / invite `{email}` |
| DELETE | `/api/groups/{groupId}/invites/{inviteId}` | owner; cancel |
| GET | `/api/invites` | invites addressed to my email |
| POST | `/api/invites/{id}/accept` `/decline` | recipient only (others → 404) |
| POST | `/api/invite-links/preview` `/accept` | body `{token}`; accept is idempotent |
| GET/POST | `/api/groups/{groupId}/expenses` | member; paged newest first / add (`paidBy`, `splitType`, `participants[{userId, value}]`) |
| POST | `/api/groups/{groupId}/expenses/preview` | member; `{amount, splitType, participants}` → shares, nothing saved |
| GET/PUT/DELETE | `/api/groups/{groupId}/expenses/{expenseId}` | member; PUT/DELETE creator or owner; PUT requires `version` |
| GET | `/api/groups/{groupId}/balances` | member; `{currency, members[{user, paid, owed, sent, received, net}], suggestedPayments[{from, to, amount}]}` |
| GET/POST | `/api/groups/{groupId}/settlements` | member; list / record `{fromUserId, toUserId, amount, settledOn, note}` (party or owner) |
| DELETE | `/api/groups/{groupId}/settlements/{settlementId}` | party or owner |
| GET | `/api/balances` | my net in each group + per-currency totals `{groups[], totals[{currency, owedToYou, youOwe, net}]}` |
| GET | `/api/expenses/monthly?to=YYYY-MM&months=1..24` | `[{month, total, count}]`, oldest first, zero-filled (default: 6 months to the current month) |
| GET | `/api/budgets?month=YYYY-MM` | `[{id, category?, amount, spent, remaining, percentUsed, overBudget}]`, overall first |
| POST | `/api/budgets` | `{categoryId?, amount}`; duplicate → 409; foreign/unknown category → 400 |
| PUT/DELETE | `/api/budgets/{id}` | own only (404 otherwise); PUT `{amount}` |
| GET | `/api/admin/stats` | ADMIN; `{users{…}, content{…}, monthly[{month, newUsers, newExpenses}]}` (6 months, UTC) |
| POST | `/api/admin/users/{userId}/disable` `/enable` | ADMIN; idempotent; can't disable yourself |

## 8. Database (after V5)

- `users` (id, email unique lowercase, password_hash bcrypt, display_name, role USER|ADMIN, currency, **enabled**, timestamps)
- `refresh_tokens` (user_id, token_hash unique, expires_at, revoked_at)
- `categories` (user_id **nullable** = built-in, name, color hex). Unique index on `(COALESCE(user_id, zero-uuid), lower(name))`. 11 seeded built-ins.
- `expenses` (user_id, category_id nullable `ON DELETE SET NULL`, description, amount NUMERIC(12,2) > 0, expense_date DATE, notes, version, timestamps)
- `groups` (name, description?, currency, created_by `ON DELETE SET NULL`, invite_token_hash unique?, invite_expires_at?, timestamps). CHECK: hash and expiry are both null or both set.
- `group_members` (group_id cascade, user_id cascade, role OWNER|MEMBER, joined_at, unique (group_id, user_id))
- `group_invites` (group_id cascade, email lowercase, invited_by `ON DELETE SET NULL`, created_at, unique (group_id, email)). Row = pending; deleted on accept, decline or cancel. Joining by any route clears the user's pending invite for that group.
- `group_expenses` (group_id cascade, paid_by → users, description, amount > 0, expense_date, split_type, created_by `ON DELETE SET NULL`, version, timestamps)
- `group_expense_shares` (expense_id cascade, user_id → users, sort_order, share_input NULL for EQUAL, owed ≥ 0, unique (expense_id, user_id))
- `settlements` (group_id cascade, from_user, to_user, amount > 0, settled_on, note, created_by, created_at, CHECK from ≠ to)
- `budgets` (user_id cascade, category_id nullable = overall, cascade; amount > 0; timestamps; `UNIQUE NULLS NOT DISTINCT (user_id, category_id)`)
- V5 also added indexes on `group_expenses(paid_by)`, `settlements(from_user)`, `settlements(to_user)` for the cross-group summary.
- References to `users` from the ledger tables are plain FKs (no cascade). There's no account deletion yet; if it's added, decide what happens to ledger history.

---

## 9. Gotchas already hit (save yourself the time)

- **Spring Boot 4 moved test packages:** `AutoConfigureMockMvc` is now in `org.springframework.boot.webmvc.test.autoconfigure`. The starters are modular (`spring-boot-starter-webmvc`, `-flyway`, `-security-oauth2-resource-server`, and matching `-test` starters).
- **Jackson 3** (`tools.jackson.*`) is the default. Tests avoid ObjectMapper and use text-block JSON plus JsonPath.
- **JsonPath filter expressions** (`$[?(@.name=='X')]`) behaved unexpectedly in `jsonPath()` matchers. Use the `IntegrationTest` helpers `categories(token)`, `category(token, name)`, `categoryId(token, name)` and AssertJ instead.
- `TestcontainersConfiguration` had to be made `public` so tests in subpackages can import it.
- `CriteriaQuery.multiselect` is deprecated (JPA 3.2). Use `query.select(cb.tuple(...))`.
- In JPQL, filter a nullable relation with an explicit `left join` (`left join c.owner o where o is null or o.id = :userId`).
- After updating a `@Version` entity, call `saveAndFlush` so the response carries the incremented version.
- The entity class is named `Group` even though `GROUP` is a JPQL keyword. Hibernate 7 accepts it, including in derived queries, so there's no need to rename it.
- JPQL constructor expressions work well for list DTOs with counts (see `GroupMemberRepository.findSummaries`); a `count()` subquery maps onto a `long` record component.
- Test helpers in `IntegrationTest`: `newUser()` → `TestUser(token, id, email)`, `as(user, request)`, `createGroup(owner, name)`, `addMember(owner, groupId, member)` (via invite link). `JdbcTemplate` is handy for time travel (see `InviteIntegrationTest.expiredLinksDontWork`).
- Big bash heredocs containing apostrophes still break after the RTK rewrite (hit again in Phase 3). Write scripts to a file with the Write tool and run that file instead.
- Tests create fresh users with random emails and share one Spring context and container; there's no DB cleanup between tests, so **assert per user, not global counts**.
- Every test user is called "Test User". `BalanceAndSettlementIntegrationTest` renames them through `PUT /api/users/me` when it needs names in messages or ordering. `ledger/LedgerTestSupport` has the expense and settlement JSON helpers.
- Surefire's `.txt` report shows 0 tests for classes that use `@Nested` (e.g. `SplitCalculatorTest`); the counts are in the `TEST-*.xml` file.
- `IntegrationTest.TestUser` is a protected nested record, so its constructor can't be called from test subpackages. Use `testUser(mvcResult)` to build one from a register/login response (the admin tests do this for the seeded admin).
- Admin tests log in as the seeded `admin@splitledger.dev` / `admin12345`. Stats tests assert **deltas** (before/after), never absolute counts.
- Don't name a JPA property `position`: it collides with the HQL `position()` function in `@OrderBy`. That's why the column is `sort_order`.

---

## 10. Next step: Phase 6 (ship)

From PLAN.md; confirm the details with the developer (offer options with AskUserQuestion):
- **Git first:** nothing is committed yet. Ask about an initial commit (and maybe one commit per phase) before setting up CI.
- **GitHub Actions:** backend `./mvnw test` (Testcontainers works on `ubuntu-latest`, which has Docker) plus frontend `npm ci && npm run build && npm run lint`.
- **Deploy: Oracle Cloud Always Free VM** (decided 2026-10: the developer can't spend money and already has an OCI account). Railway/Fly/Koyeb no longer have usable free tiers; Render + Neon (no card, but cold starts) is the fallback.
  - **Done:** `docker-compose.prod.yml` (Caddy for automatic HTTPS as the only public service; no published DB/backend ports; required secrets via `${VAR:?}`; `restart: unless-stopped`; capped json-file logs; backend `mem_limit: 1g`; `COOKIE_SECURE` forced on), `deploy/Caddyfile` (domain from `SITE_ADDRESS`, HSTS and security headers), `deploy/backup.sh` (gzip `pg_dump`, keeps 14), `deploy/DEPLOY.md` (step-by-step OCI guide: A1.Flex 1 OCPU/6 GB Ubuntu 24.04 aarch64, security list + iptables, DuckDNS, Docker, `.env`, cron backups, updates), and `.gitattributes` (LF for `*.sh`/`mvnw`/Caddyfile, since `core.autocrlf=true` would break them).
  - **Verified locally:** the prod stack ran under `-p sl-prodtest` with `SITE_ADDRESS=localhost`: HTTP 308 → HTTPS, security headers, cookie `Secure; HttpOnly; SameSite=Strict`, login → refresh → admin stats over HTTPS, dev default admin password rejected, port 8080 not reachable, backup + retention works. The test stack and its volumes were removed afterwards.
  - **Not done (needs the developer):** create the VM, open both firewalls, set up DuckDNS, push the repo to GitHub so the server can clone it. Not verified on real OCI/ARM yet.
  - Still to do: a seeded demo account for recruiters.
- **README:** architecture diagram, ER diagram, screenshots (needs the browser pass first).
- The browser pass on the UI is still owed (see section 2).

Phase 5 decisions (made with the developer): personal and group spending kept separate; recurring budgets (per category + optional overall); disabling takes effect immediately (per-request check); hand-written charts, no chart library.

## 11. Working style the developer has responded well to

- Explain the plan briefly, then build. Verify with real runs (tests, build, Docker smoke test) and report honestly what was and wasn't verified.
- Update `PLAN.md` checkboxes and the README "Design decisions" section at the end of each phase.
- The developer is learning parts of the stack (they asked what Flyway and Zod are). Short explanations tied to this codebase's actual files work well.
- Don't commit or push unless asked.

---

### Suggested opener for the new chat

> I'm continuing my SplitLedger portfolio project in `Portfolio Projects/splitledger`. Read `HANDOFF.md`, `PLAN.md` and `README.md` first, then let's start Phase 6 (CI, deploy, README diagrams).
