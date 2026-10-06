export type Role = 'USER' | 'ADMIN'

export interface User {
  id: string
  email: string
  displayName: string
  role: Role
  currency: string
  /** False once an admin has disabled the account. */
  enabled: boolean
  createdAt: string
}

export interface AuthResponse {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
  user: User
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

/** RFC 9457 problem detail, as returned by the Spring backend. */
export interface ProblemDetail {
  type?: string
  title?: string
  status: number
  detail?: string
  errors?: Record<string, string>
}

export interface Category {
  id: string
  name: string
  color: string
  /** Built-in categories are shared by everyone and can't be edited. */
  system: boolean
}

export interface Expense {
  id: string
  description: string
  amount: number
  /** ISO date, e.g. 2026-09-23 */
  expenseDate: string
  category: Category | null
  notes: string | null
  version: number
  createdAt: string
  updatedAt: string
}

export interface ExpenseInput {
  description: string
  amount: number
  expenseDate: string
  categoryId: string | null
  notes: string | null
  version?: number
}

export interface ExpenseFilters {
  from?: string
  to?: string
  categoryId?: string
  q?: string
}

export interface CategoryTotal {
  categoryId: string | null
  name: string | null
  color: string | null
  total: number
  count: number
}

export interface ExpenseSummary {
  total: number
  count: number
  byCategory: CategoryTotal[]
}

/** A user's role within one group (separate from the app-wide Role). */
export type GroupRole = 'OWNER' | 'MEMBER'

export interface GroupSummary {
  id: string
  name: string
  description: string | null
  currency: string
  myRole: GroupRole
  memberCount: number
  createdAt: string
}

export interface GroupMember {
  userId: string
  displayName: string
  email: string
  role: GroupRole
  joinedAt: string
}

export interface GroupDetail {
  id: string
  name: string
  description: string | null
  currency: string
  myRole: GroupRole
  /** Owners first, then by name. */
  members: GroupMember[]
  /** Only sent to owners, and only while a link is active. */
  inviteLink: { expiresAt: string } | null
  createdAt: string
}

export interface GroupInput {
  name: string
  description: string | null
  currency: string
}

/** The token is only ever returned here: the server stores just its hash. */
export interface CreatedInviteLink {
  token: string
  expiresAt: string
}

export interface InviteLinkPreview {
  groupId: string
  groupName: string
  memberCount: number
  alreadyMember: boolean
}

/** A pending email invite, as a group owner sees it. */
export interface GroupInvite {
  id: string
  email: string
  invitedBy: string | null
  createdAt: string
}

/** A pending email invite addressed to the signed-in user. */
export interface MyInvite {
  id: string
  groupId: string
  groupName: string
  invitedBy: string | null
  createdAt: string
}

// --- Group expenses, balances and settlements (Phase 4) ---

export type SplitType = 'EQUAL' | 'EXACT' | 'PERCENT' | 'SHARES'

/** A person as shown next to money. */
export interface UserRef {
  userId: string
  displayName: string
}

export interface ExpenseShare {
  userId: string
  displayName: string
  /** What was entered: the amount, percentage or number of shares. Null for EQUAL splits. */
  value: number | null
  owed: number
}

export interface GroupExpense {
  id: string
  description: string
  amount: number
  expenseDate: string
  splitType: SplitType
  paidBy: UserRef
  createdBy: UserRef | null
  /** In the order they were entered. */
  shares: ExpenseShare[]
  /** Whether the current user may edit or delete it (its creator or a group owner). */
  canEdit: boolean
  version: number
  createdAt: string
  updatedAt: string
}

export interface SplitParticipant {
  userId: string
  value: number | null
}

export interface SplitPreviewInput {
  amount: number
  splitType: SplitType
  participants: SplitParticipant[]
}

export interface GroupExpenseInput extends SplitPreviewInput {
  description: string
  expenseDate: string
  paidBy: string
  version?: number
}

export interface MemberBalance {
  user: UserRef
  paid: number
  owed: number
  sent: number
  received: number
  /** Positive: the group owes them. Negative: they owe the group. */
  net: number
}

export interface SuggestedPayment {
  from: UserRef
  to: UserRef
  amount: number
}

export interface GroupBalances {
  currency: string
  members: MemberBalance[]
  /** The fewest payments that settle everyone up. */
  suggestedPayments: SuggestedPayment[]
}

export interface Settlement {
  id: string
  from: UserRef
  to: UserRef
  amount: number
  settledOn: string
  note: string | null
  createdBy: UserRef | null
  canDelete: boolean
  createdAt: string
}

export interface SettlementInput {
  fromUserId: string
  toUserId: string
  amount: number
  settledOn: string
  note: string | null
}

// --- Insights, budgets and admin (Phase 5) ---

export interface MonthTotal {
  /** YYYY-MM */
  month: string
  total: number
  count: number
}

export interface BudgetStatus {
  id: string
  /** Null for the overall budget. */
  category: Category | null
  amount: number
  spent: number
  /** Negative when over budget. */
  remaining: number
  /** Whole percent, rounded down. Can exceed 100. */
  percentUsed: number
  overBudget: boolean
}

export interface GroupNet {
  groupId: string
  groupName: string
  currency: string
  /** Positive: the group owes you. Negative: you owe the group. */
  net: number
}

/** Across all groups in one currency. Both amounts are non-negative. */
export interface CurrencyTotal {
  currency: string
  owedToYou: number
  youOwe: number
  net: number
}

export interface MyBalances {
  groups: GroupNet[]
  totals: CurrencyTotal[]
}

export interface AdminStats {
  users: { total: number; admins: number; disabled: number; newLast30Days: number; activeLast30Days: number }
  content: { groups: number; personalExpenses: number; groupExpenses: number; settlements: number }
  monthly: { month: string; newUsers: number; newExpenses: number }[]
}
