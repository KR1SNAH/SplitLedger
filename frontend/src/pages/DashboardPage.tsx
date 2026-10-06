import { useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import { useAuth } from '@/auth/useAuth'
import { CategoryBadge } from '@/components/CategoryBadge'
import { ColumnChart } from '@/components/charts/ColumnChart'
import { EmptyState } from '@/components/EmptyState'
import { Button } from '@/components/ui/Button'
import { useBudgets } from '@/features/budgets/api'
import { BudgetMeter } from '@/features/budgets/BudgetMeter'
import { useExpenses, useExpenseSummary, useMonthlyTotals } from '@/features/expenses/api'
import { ExpenseFormDialog } from '@/features/expenses/ExpenseFormDialog'
import { useMyBalances } from '@/features/ledger/api'
import { currentMonth, formatDate, formatMonth, formatMoney, monthRange } from '@/lib/format'
import type { BudgetStatus, ExpenseSummary, MyBalances } from '@/lib/types'

const TREND_MONTHS = 6

export function DashboardPage() {
  const { user } = useAuth()
  const currency = user?.currency ?? 'USD'
  // Fixed for the page's lifetime, so the numbers don't shift if it stays open past midnight.
  const [month] = useState(currentMonth)
  const summary = useExpenseSummary(monthRange(month))
  const budgets = useBudgets(month)
  const balances = useMyBalances()
  const [formOpen, setFormOpen] = useState(false)

  const monthName = formatMonth(month, 'name')

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Hi, {user?.displayName}</h1>
          <p className="mt-1 text-slate-600">Here's your spending at a glance.</p>
        </div>
        <Button onClick={() => setFormOpen(true)}>+ Add expense</Button>
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <StatTile label={`Spent in ${monthName}`}>
          <p className="mt-2 text-3xl font-semibold">{summary.data ? formatMoney(summary.data.total, currency) : '—'}</p>
          <p className="mt-1 text-sm text-slate-500">
            {summary.data ? `${summary.data.count} ${summary.data.count === 1 ? 'expense' : 'expenses'}` : ' '}
          </p>
        </StatTile>
        <OverallBudgetTile budgets={budgets.data} currency={currency} monthName={monthName} />
        <GroupsTile balances={balances.data} />
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card title="Monthly spending" subtitle={`Last ${TREND_MONTHS} months`}>
          <SpendingTrend month={month} currency={currency} />
        </Card>
        <Card title={`Top categories in ${monthName}`}>
          <TopCategories summary={summary.data} currency={currency} />
        </Card>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card title={`Budgets in ${monthName}`} link={{ to: '/budgets', label: 'Manage' }}>
          <CategoryBudgets budgets={budgets.data} currency={currency} />
        </Card>
        <Card title="Group balances" link={{ to: '/groups', label: 'All groups' }}>
          <GroupBalances balances={balances.data} />
        </Card>
      </div>

      <Card title="Recent expenses" link={{ to: '/expenses', label: 'View all' }}>
        <RecentExpenses currency={currency} onAdd={() => setFormOpen(true)} />
      </Card>

      <ExpenseFormDialog open={formOpen} onClose={() => setFormOpen(false)} />
    </div>
  )
}

function StatTile({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-5">
      <p className="text-sm text-slate-600">{label}</p>
      {children}
    </div>
  )
}

function Card({
  title,
  subtitle,
  link,
  children,
}: {
  title: string
  subtitle?: string
  link?: { to: string; label: string }
  children: ReactNode
}) {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5">
      <div className="flex items-baseline justify-between gap-4">
        <div>
          <h2 className="font-semibold">{title}</h2>
          {subtitle && <p className="text-xs text-slate-500">{subtitle}</p>}
        </div>
        {link && (
          <Link to={link.to} className="text-sm font-medium text-emerald-700 hover:underline">
            {link.label}
          </Link>
        )}
      </div>
      <div className="mt-4">{children}</div>
    </section>
  )
}

function OverallBudgetTile({
  budgets,
  currency,
  monthName,
}: {
  budgets: BudgetStatus[] | undefined
  currency: string
  monthName: string
}) {
  const overall = budgets?.find((b) => b.category === null)
  if (budgets && !overall) {
    return (
      <StatTile label="Monthly budget">
        <p className="mt-2 text-3xl font-semibold text-slate-400">Not set</p>
        <Link to="/budgets" className="mt-1 inline-block text-sm font-medium text-emerald-700 hover:underline">
          Set an overall budget
        </Link>
      </StatTile>
    )
  }
  return (
    <StatTile label={overall?.overBudget ? `Over your ${monthName} budget by` : `Left in your ${monthName} budget`}>
      <p className={`mt-2 text-3xl font-semibold ${overall?.overBudget ? 'text-red-700' : ''}`}>
        {overall ? formatMoney(Math.abs(overall.remaining), currency) : '—'}
      </p>
      <p className="mt-1 text-sm text-slate-500">
        {overall ? `${overall.percentUsed}% of ${formatMoney(overall.amount, currency)} used` : ' '}
      </p>
    </StatTile>
  )
}

function GroupsTile({ balances }: { balances: MyBalances | undefined }) {
  const outstanding = balances?.totals.filter((t) => t.net !== 0) ?? []
  return (
    <StatTile label="Across your groups">
      {!balances ? (
        <p className="mt-2 text-3xl font-semibold">—</p>
      ) : balances.groups.length === 0 ? (
        <>
          <p className="mt-2 text-3xl font-semibold text-slate-400">No groups</p>
          <Link to="/groups" className="mt-1 inline-block text-sm font-medium text-emerald-700 hover:underline">
            Start a group
          </Link>
        </>
      ) : outstanding.length === 0 ? (
        <>
          <p className="mt-2 text-3xl font-semibold">Settled up</p>
          <p className="mt-1 text-sm text-slate-500">Nobody owes anybody</p>
        </>
      ) : (
        // No currency conversion, so each currency gets its own line.
        <ul className="mt-2 space-y-1">
          {outstanding.map((total) => (
            <li key={total.currency}>
              <span className={`text-2xl font-semibold ${total.net > 0 ? 'text-emerald-700' : 'text-red-700'}`}>
                {formatMoney(Math.abs(total.net), total.currency)}
              </span>{' '}
              <span className="text-sm text-slate-600">{total.net > 0 ? 'owed to you' : 'you owe'}</span>
            </li>
          ))}
        </ul>
      )}
    </StatTile>
  )
}

function SpendingTrend({ month, currency }: { month: string; currency: string }) {
  const { data } = useMonthlyTotals(month, TREND_MONTHS)
  if (!data) return <div className="h-52" />
  if (data.every((m) => m.count === 0)) {
    return <p className="text-sm text-slate-500">No spending in the last {TREND_MONTHS} months.</p>
  }
  return (
    <ColumnChart
      title={`Spending per month, last ${TREND_MONTHS} months`}
      columns={data.map((m) => ({
        key: m.month,
        label: formatMonth(m.month, 'short'),
        fullLabel: formatMonth(m.month),
        value: m.total,
        valueLabel: formatMoney(m.total, currency),
      }))}
    />
  )
}

function TopCategories({ summary, currency }: { summary: ExpenseSummary | undefined; currency: string }) {
  const top = summary?.byCategory.slice(0, 5) ?? []
  const largest = top[0]?.total ?? 0
  if (summary && top.length === 0) return <p className="text-sm text-slate-500">Nothing spent yet this month.</p>
  return (
    <ul className="space-y-3">
      {top.map((row) => (
        <li key={row.categoryId ?? 'none'}>
          <div className="flex items-center justify-between text-sm">
            <CategoryBadge category={row.name ? { name: row.name, color: row.color ?? '#cbd5e1' } : null} />
            <span className="font-medium tabular-nums">{formatMoney(row.total, currency)}</span>
          </div>
          <div className="mt-1.5 h-2 rounded-full bg-slate-100">
            <div
              className="h-2 rounded-full"
              style={{
                width: `${largest ? (row.total / largest) * 100 : 0}%`,
                backgroundColor: row.color ?? '#cbd5e1',
              }}
            />
          </div>
        </li>
      ))}
    </ul>
  )
}

const DASHBOARD_BUDGETS = 4

/** The category budgets closest to (or over) their limit. The overall budget has its own tile. */
function CategoryBudgets({ budgets, currency }: { budgets: BudgetStatus[] | undefined; currency: string }) {
  if (!budgets) return null
  const byCategory = budgets
    .filter((b) => b.category !== null)
    .sort((a, b) => b.percentUsed - a.percentUsed)
    .slice(0, DASHBOARD_BUDGETS)
  if (byCategory.length === 0) {
    return (
      <p className="text-sm text-slate-500">
        No category budgets yet.{' '}
        <Link to="/budgets" className="font-medium text-emerald-700 hover:underline">
          Add one
        </Link>{' '}
        to see how close you are to your limits.
      </p>
    )
  }
  return (
    <ul className="space-y-4">
      {byCategory.map((budget) => (
        <li key={budget.id}>
          <BudgetMeter budget={budget} currency={currency} />
        </li>
      ))}
    </ul>
  )
}

function GroupBalances({ balances }: { balances: MyBalances | undefined }) {
  if (!balances) return null
  if (balances.groups.length === 0) {
    return <p className="text-sm text-slate-500">You're not in any groups yet.</p>
  }
  const outstanding = balances.groups.filter((g) => g.net !== 0)
  if (outstanding.length === 0) {
    return <p className="text-sm text-slate-500">You're settled up in all {balances.groups.length} of your groups.</p>
  }
  return (
    <ul className="divide-y divide-slate-100">
      {outstanding.map((group) => (
        <li key={group.groupId}>
          <Link
            to={`/groups/${group.groupId}`}
            className="-mx-2 flex items-center justify-between gap-4 rounded-lg px-2 py-2.5 hover:bg-slate-50"
          >
            <span className="truncate text-sm font-medium">{group.groupName}</span>
            <span className={`text-sm tabular-nums ${group.net > 0 ? 'text-emerald-700' : 'text-red-600'}`}>
              {group.net > 0 ? "you're owed " : 'you owe '}
              {formatMoney(Math.abs(group.net), group.currency)}
            </span>
          </Link>
        </li>
      ))}
    </ul>
  )
}

function RecentExpenses({ currency, onAdd }: { currency: string; onAdd: () => void }) {
  const recent = useExpenses({ size: 5, sort: 'date,desc' })
  if (recent.data && recent.data.content.length === 0) {
    return (
      <EmptyState
        title="No expenses yet"
        description="Add your first expense to get started."
        action={<Button onClick={onAdd}>Add expense</Button>}
      />
    )
  }
  return (
    <ul className="divide-y divide-slate-100">
      {recent.data?.content.map((expense) => (
        <li key={expense.id} className="flex items-center justify-between gap-4 py-2.5">
          <div className="min-w-0">
            <p className="truncate text-sm font-medium">{expense.description}</p>
            <p className="text-xs text-slate-500">
              {formatDate(expense.expenseDate)} · {expense.category?.name ?? 'Uncategorized'}
            </p>
          </div>
          <span className="text-sm font-medium tabular-nums">{formatMoney(expense.amount, currency)}</span>
        </li>
      ))}
    </ul>
  )
}
