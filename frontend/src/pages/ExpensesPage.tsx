import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router'
import { useAuth } from '@/auth/useAuth'
import { CategoryBadge } from '@/components/CategoryBadge'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { EmptyState } from '@/components/EmptyState'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { inputClass } from '@/components/ui/styles'
import { useCategories } from '@/features/categories/api'
import { useDeleteExpense, useExpenses, useExpenseSummary } from '@/features/expenses/api'
import { ExpenseFormDialog } from '@/features/expenses/ExpenseFormDialog'
import { useDebouncedValue } from '@/hooks/useDebouncedValue'
import { cn } from '@/lib/cn'
import { formatDate, formatMoney } from '@/lib/format'
import type { Expense, ExpenseFilters } from '@/lib/types'

const PAGE_SIZE = 20

const SORT_OPTIONS = [
  { value: 'date,desc', label: 'Newest first' },
  { value: 'date,asc', label: 'Oldest first' },
  { value: 'amount,desc', label: 'Highest amount' },
  { value: 'amount,asc', label: 'Lowest amount' },
]

export function ExpensesPage() {
  const { user } = useAuth()
  const currency = user?.currency ?? 'USD'
  const { data: categories = [] } = useCategories()

  // Filters live in the URL so views are shareable and survive reloads / back navigation.
  const [searchParams, setSearchParams] = useSearchParams()
  const filters: ExpenseFilters = {
    from: searchParams.get('from') ?? undefined,
    to: searchParams.get('to') ?? undefined,
    categoryId: searchParams.get('categoryId') ?? undefined,
    q: searchParams.get('q') ?? undefined,
  }
  const page = Number(searchParams.get('page') ?? 0)
  const sort = searchParams.get('sort') ?? 'date,desc'

  const updateParams = (changes: Record<string, string | undefined>, resetPage = true) => {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        Object.entries(changes).forEach(([key, value]) => (value ? next.set(key, value) : next.delete(key)))
        if (resetPage) next.delete('page')
        return next
      },
      { replace: true },
    )
  }

  // Debounce typing in the search box before it hits the URL (and the API).
  const [searchInput, setSearchInput] = useState(filters.q ?? '')
  const debouncedSearch = useDebouncedValue(searchInput)
  useEffect(() => {
    if ((debouncedSearch || undefined) !== filters.q) updateParams({ q: debouncedSearch || undefined })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedSearch])

  const expenses = useExpenses({ ...filters, page, size: PAGE_SIZE, sort })
  const summary = useExpenseSummary(filters)
  const deleteExpense = useDeleteExpense()

  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<Expense | null>(null)
  const [deleting, setDeleting] = useState<Expense | null>(null)

  const hasFilters = Object.values(filters).some(Boolean)
  const data = expenses.data

  function openCreate() {
    setEditing(null)
    setFormOpen(true)
  }

  function openEdit(expense: Expense) {
    setEditing(expense)
    setFormOpen(true)
  }

  async function confirmDelete() {
    if (!deleting) return
    try {
      await deleteExpense.mutateAsync(deleting.id)
      setDeleting(null)
    } catch {
      // The error is shown inside the dialog via deleteExpense.error.
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Expenses</h1>
          <p className="mt-1 text-slate-600">
            {summary.data ? (
              <>
                <span className="font-medium text-slate-900">{formatMoney(summary.data.total, currency)}</span> across{' '}
                {summary.data.count} {summary.data.count === 1 ? 'expense' : 'expenses'}
                {hasFilters && ' matching your filters'}
              </>
            ) : (
              'Loading…'
            )}
          </p>
        </div>
        <Button onClick={openCreate}>+ Add expense</Button>
      </div>

      <div className="grid gap-3 rounded-2xl border border-slate-200 bg-white p-4 sm:grid-cols-2 lg:grid-cols-5">
        <input
          type="search"
          placeholder="Search descriptions…"
          aria-label="Search descriptions"
          value={searchInput}
          onChange={(e) => setSearchInput(e.target.value)}
          className={cn(inputClass(), 'lg:col-span-2')}
        />
        <select
          aria-label="Category"
          value={filters.categoryId ?? ''}
          onChange={(e) => updateParams({ categoryId: e.target.value || undefined })}
          className={inputClass()}
        >
          <option value="">All categories</option>
          {categories.map((category) => (
            <option key={category.id} value={category.id}>
              {category.name}
            </option>
          ))}
        </select>
        <input
          type="date"
          aria-label="From date"
          value={filters.from ?? ''}
          max={filters.to}
          onChange={(e) => updateParams({ from: e.target.value || undefined })}
          className={inputClass()}
        />
        <input
          type="date"
          aria-label="To date"
          value={filters.to ?? ''}
          min={filters.from}
          onChange={(e) => updateParams({ to: e.target.value || undefined })}
          className={inputClass()}
        />
        <div className="flex items-center justify-between gap-3 sm:col-span-2 lg:col-span-5">
          <select
            aria-label="Sort"
            value={sort}
            onChange={(e) => updateParams({ sort: e.target.value })}
            className={cn(inputClass(), 'w-auto')}
          >
            {SORT_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
          {hasFilters && (
            <Button
              variant="ghost"
              onClick={() => {
                setSearchInput('')
                updateParams({ q: undefined, categoryId: undefined, from: undefined, to: undefined })
              }}
            >
              Clear filters
            </Button>
          )}
        </div>
      </div>

      {expenses.isError && <Alert>{expenses.error.message}</Alert>}

      <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white">
        {data && data.content.length === 0 ? (
          hasFilters ? (
            <EmptyState title="No matching expenses" description="Try a different search or clear the filters." />
          ) : (
            <EmptyState
              title="No expenses yet"
              description="Add your first expense to start seeing where your money goes."
              action={<Button onClick={openCreate}>Add expense</Button>}
            />
          )
        ) : (
          <div className="overflow-x-auto">
            <table className={cn('min-w-full text-sm', expenses.isPlaceholderData && 'opacity-60')}>
              <thead className="bg-slate-50 text-left text-slate-600">
                <tr>
                  <th className="px-4 py-3 font-medium">Date</th>
                  <th className="px-4 py-3 font-medium">Description</th>
                  <th className="px-4 py-3 font-medium">Category</th>
                  <th className="px-4 py-3 text-right font-medium">Amount</th>
                  <th className="px-4 py-3">
                    <span className="sr-only">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {expenses.isPending && (
                  <tr>
                    <td colSpan={5} className="px-4 py-8 text-center text-slate-500">
                      Loading…
                    </td>
                  </tr>
                )}
                {data?.content.map((expense) => (
                  <tr key={expense.id} className="hover:bg-slate-50">
                    <td className="whitespace-nowrap px-4 py-3 text-slate-600">{formatDate(expense.expenseDate)}</td>
                    <td className="px-4 py-3">
                      <div className="font-medium">{expense.description}</div>
                      {expense.notes && <div className="line-clamp-1 text-xs text-slate-500">{expense.notes}</div>}
                    </td>
                    <td className="px-4 py-3">
                      <CategoryBadge category={expense.category} />
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-right font-medium tabular-nums">
                      {formatMoney(expense.amount, currency)}
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-right">
                      <Button variant="ghost" className="px-2 py-1" onClick={() => openEdit(expense)}>
                        Edit
                      </Button>
                      <Button variant="ghost" className="px-2 py-1 text-red-600" onClick={() => setDeleting(expense)}>
                        Delete
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-end gap-2">
          <Button
            variant="secondary"
            disabled={page === 0}
            onClick={() => updateParams({ page: String(page - 1) }, false)}
          >
            Previous
          </Button>
          <span className="text-sm text-slate-600">
            Page {page + 1} of {data.totalPages}
          </span>
          <Button
            variant="secondary"
            disabled={page + 1 >= data.totalPages}
            onClick={() => updateParams({ page: String(page + 1) }, false)}
          >
            Next
          </Button>
        </div>
      )}

      <ExpenseFormDialog open={formOpen} onClose={() => setFormOpen(false)} expense={editing} />
      <ConfirmDialog
        open={deleting !== null}
        title="Delete expense?"
        confirmLabel="Delete"
        onConfirm={confirmDelete}
        onClose={() => {
          setDeleting(null)
          deleteExpense.reset()
        }}
        pending={deleteExpense.isPending}
        error={deleteExpense.error?.message}
      >
        <strong className="text-slate-900">{deleting?.description}</strong> (
        {deleting && formatMoney(deleting.amount, currency)}) will be permanently deleted.
      </ConfirmDialog>
    </div>
  )
}
