import { useState } from 'react'
import { useSearchParams } from 'react-router'
import { useAuth } from '@/auth/useAuth'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { EmptyState } from '@/components/EmptyState'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { useBudgets, useDeleteBudget } from '@/features/budgets/api'
import { BudgetFormDialog } from '@/features/budgets/BudgetFormDialog'
import { BudgetMeter } from '@/features/budgets/BudgetMeter'
import { currentMonth, formatMonth, shiftMonth } from '@/lib/format'
import type { BudgetStatus } from '@/lib/types'

const MONTH_PATTERN = /^\d{4}-(0[1-9]|1[0-2])$/

export function BudgetsPage() {
  const { user } = useAuth()
  const currency = user?.currency ?? 'USD'
  // The month lives in the URL, like the expense filters, so it survives reloads and can be linked.
  const [searchParams, setSearchParams] = useSearchParams()
  const param = searchParams.get('month')
  const thisMonth = currentMonth()
  const month = param && MONTH_PATTERN.test(param) ? param : thisMonth

  const { data: budgets, isPending, isError, error } = useBudgets(month)
  const deleteBudget = useDeleteBudget()
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<BudgetStatus | null>(null)
  const [deleting, setDeleting] = useState<BudgetStatus | null>(null)

  function goTo(target: string) {
    setSearchParams(target === thisMonth ? {} : { month: target }, { replace: true })
  }

  function openForm(budget: BudgetStatus | null) {
    setEditing(budget)
    setFormOpen(true)
  }

  async function confirmDelete() {
    if (!deleting) return
    try {
      await deleteBudget.mutateAsync(deleting.id)
      setDeleting(null)
    } catch {
      // Shown in the dialog via deleteBudget.error.
    }
  }

  const overCount = budgets?.filter((b) => b.overBudget).length ?? 0

  return (
    <div className="max-w-3xl space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Budgets</h1>
          <p className="mt-1 text-slate-600">Monthly limits on your personal spending. They repeat every month.</p>
        </div>
        <Button onClick={() => openForm(null)}>+ New budget</Button>
      </div>

      <div className="flex items-center justify-between gap-3 rounded-2xl border border-slate-200 bg-white px-4 py-3">
        <Button variant="ghost" className="px-2 py-1" onClick={() => goTo(shiftMonth(month, -1))} aria-label="Previous month">
          ← Prev
        </Button>
        <div className="text-center">
          <p className="font-semibold">{formatMonth(month)}</p>
          {month !== thisMonth && (
            <button type="button" className="text-xs font-medium text-emerald-700 hover:underline" onClick={() => goTo(thisMonth)}>
              Back to this month
            </button>
          )}
        </div>
        <Button variant="ghost" className="px-2 py-1" onClick={() => goTo(shiftMonth(month, 1))} aria-label="Next month">
          Next →
        </Button>
      </div>

      {isError && <Alert>{error.message}</Alert>}

      <section className="rounded-2xl border border-slate-200 bg-white p-6">
        {isPending ? (
          <p className="text-sm text-slate-500">Loading…</p>
        ) : budgets && budgets.length === 0 ? (
          <EmptyState
            title="No budgets yet"
            description="Set a monthly limit for a category, or for all your spending, and track how close you are."
            action={<Button onClick={() => openForm(null)}>Create a budget</Button>}
          />
        ) : (
          <>
            {overCount > 0 && (
              <p className="mb-4 text-sm font-medium text-red-700">
                {overCount === 1 ? '1 budget is' : `${overCount} budgets are`} over the limit in {formatMonth(month, 'name')}.
              </p>
            )}
            <ul className="space-y-5">
              {budgets?.map((budget) => (
                <li key={budget.id}>
                  <BudgetMeter
                    budget={budget}
                    currency={currency}
                    actions={
                      <span className="flex">
                        <Button variant="ghost" className="px-2 py-1" onClick={() => openForm(budget)}>
                          Edit
                        </Button>
                        <Button variant="ghost" className="px-2 py-1 text-red-600" onClick={() => setDeleting(budget)}>
                          Delete
                        </Button>
                      </span>
                    }
                  />
                </li>
              ))}
            </ul>
          </>
        )}
      </section>

      <BudgetFormDialog open={formOpen} onClose={() => setFormOpen(false)} budget={editing} existing={budgets ?? []} />

      <ConfirmDialog
        open={deleting !== null}
        title="Delete budget?"
        confirmLabel="Delete"
        onConfirm={confirmDelete}
        onClose={() => {
          setDeleting(null)
          deleteBudget.reset()
        }}
        pending={deleteBudget.isPending}
        error={deleteBudget.error?.message}
      >
        The <strong className="text-slate-900">{deleting?.category?.name ?? 'overall'}</strong> budget will be deleted.
        Your expenses aren't affected.
      </ConfirmDialog>
    </div>
  )
}
