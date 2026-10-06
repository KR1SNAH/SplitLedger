# SplitLedger — Build Plan

Expense tracker + bill splitter. Spring Boot 4 (Java 25) · PostgreSQL 17 · React 19 + TypeScript · Docker.

## Phase 0 — Foundation ✅
- [x] Monorepo: `backend/`, `frontend/`, `docker-compose.yml`
- [x] Spring Boot 4.1 + Flyway + JPA (`ddl-auto=validate`), springdoc Swagger UI
- [x] Vite + React TS, Tailwind v4, React Router, TanStack Query, React Hook Form + Zod
- [x] Multi-stage Dockerfiles, nginx serving the SPA and proxying `/api`

## Phase 1 — Auth & RBAC ✅
- [x] Register / login / refresh / logout
- [x] JWT access token (15 min, in memory) + opaque rotating refresh token (14 days, HttpOnly SameSite=Strict cookie, SHA-256 hashed in DB)
- [x] Refresh token reuse detection → revoke all sessions
- [x] `USER` / `ADMIN` roles, seeded admin, `/api/admin/**` restricted
- [x] RFC 9457 problem details with field-level validation errors
- [x] Integration tests with Testcontainers
- [x] Frontend: login, register, protected routes, role-gated admin users page, silent refresh

## Phase 2 — Personal expenses ✅
- [x] Categories: 11 built-ins shared by everyone + private custom categories (case-insensitive unique names)
- [x] Deleting a custom category leaves its expenses uncategorized (`ON DELETE SET NULL`)
- [x] Expense CRUD — `NUMERIC(12,2)` / `BigDecimal`, validated to 2 decimal places
- [x] Optimistic locking: updates must send the current `version`, stale edits get 409
- [x] Filtering (date range, category, case-insensitive text search), whitelisted sorting, pagination
- [x] Summary endpoint: totals by category for any filter
- [x] Preferred display currency on the user profile
- [x] Frontend: expenses page (filters in the URL), add/edit dialog, settings (profile + categories), dashboard with real data

## Phase 3 — Groups ✅
- [x] Groups with `OWNER` / `MEMBER` roles (per-group authorization via a permission service + `@PreAuthorize`)
- [x] Multiple owners: promote / demote; the last owner can't leave or step down (enforced under a row lock)
- [x] Invite link (hashed, shown once, 7-day expiry, regenerate / disable) and in-app email invites (accept / decline / cancel)
- [x] Leave group, remove member, delete group
- [x] Integration tests for every permission rule, plus a concurrency test for the last-owner rule
- [x] Frontend: groups list with pending invitations, group detail (members, roles, invite panel), `/join/:token` page

## Phase 4 — Split engine (core business logic) ✅
- [x] Group expenses: one payer + any set of participants (members only)
- [x] Split strategies: EQUAL, EXACT, PERCENT, SHARES — validation + remainder-cent allocation (largest remainder, ties by participant order, exact integer math)
- [x] Balances per member (derived from expenses + settlements, never stored); debt simplification (greedy min cash flow, ≤ n − 1 payments)
- [x] Settlements (record payments): either party or an owner can record / delete
- [x] Optimistic locking (`@Version`) on expenses, bumped even when only the shares change
- [x] Invariant: only current members can have a non-zero balance (leave / remove blocked with 409; edits that would reopen a former member's balance are rejected); group currency locked once there's activity
- [x] Every ledger write takes the group row lock; race test for "leave vs. expense that includes you"
- [x] Thorough unit tests for the split and simplification logic, including randomized property tests
- [x] Split preview endpoint so the UI shows exact shares without doing money math
- [x] Frontend: add/edit split expense form with live preview, expense list, balances + suggested payments, "settle up", payment history

## Phase 5 — Insights & admin ✅
- [x] Dashboard: spend by category, 6-month trend chart, what you owe / are owed across all groups (per currency, never converted)
- [x] Monthly budgets per category + an optional overall budget: recurring limits, over-budget / nearly-spent indicators, Budgets page with month navigation
- [x] Admin: platform stats (users, active users, content counts, sign-ups and expenses per month) and disable / enable users
- [x] Disabling takes effect immediately: refresh tokens revoked, login refused, and a JWT validator rejects the user's current access token
- [x] Route-level code splitting (every page is a lazy chunk; main bundle 530 kB → 347 kB, no size warning)
- [x] Hand-written chart components (no chart library), keyboard- and screen-reader-accessible

## Phase 6 — Ship
- [ ] GitHub Actions: backend tests (Testcontainers) + frontend build/lint
- [x] Production compose: Caddy (automatic HTTPS), private API + database, required secrets, restart policies, log caps, DB backup script
- [ ] Deploy to an Oracle Cloud Always Free VM ([deploy/DEPLOY.md](deploy/DEPLOY.md)) with a seeded demo account
- [ ] README: architecture diagram, ER diagram, screenshots, design decisions

## Stretch
Receipt uploads (MinIO/S3) · recurring expenses (`@Scheduled`) · CSV export · multi-currency · activity feed
