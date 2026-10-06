import { useState } from 'react'
import { useAuth } from '@/auth/useAuth'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { EmptyState } from '@/components/EmptyState'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { formatDate, formatMoney } from '@/lib/format'
import type { ExpenseShare, GroupExpense, SplitType } from '@/lib/types'
import { useDeleteGroupExpense, useGroupExpenses } from './api'
import { describeSplit } from './splitTypes'

interface GroupExpenseListProps {
  groupId: string
  currency: string
  onAdd: () => void
  onEdit: (expense: GroupExpense) => void
}

export function GroupExpenseList({ groupId, currency, onAdd, onEdit }: GroupExpenseListProps) {
  const { user } = useAuth()
  const [page, setPage] = useState(0)
  const [expanded, setExpanded] = useState<string | null>(null)
  const [deleting, setDeleting] = useState<GroupExpense | null>(null)
  const { data, isPending, error, isPlaceholderData } = useGroupExpenses(groupId, page)
  const deleteExpense = useDeleteGroupExpense(groupId)

  async function confirmDelete() {
    if (!deleting) return
    try {
      await deleteExpense.mutateAsync(deleting.id)
      setDeleting(null)
      // The last item on a later page: step back so the list isn't empty.
      if (data && data.content.length === 1 && page > 0) setPage(page - 1)
    } catch {
      // Shown in the dialog.
    }
  }

  return (
    <section className="rounded-2xl border border-slate-200 bg-white">
      <div className="flex items-center justify-between gap-4 border-b border-slate-100 px-6 py-4">
        <h2 className="font-semibold">
          Expenses {data && <span className="font-normal text-slate-500">({data.totalElements})</span>}
        </h2>
        <Button onClick={onAdd}>Add expense</Button>
      </div>

      {isPending ? (
        <p className="px-6 py-4 text-sm text-slate-500">Loading…</p>
      ) : !data ? (
        <div className="p-6">
          <Alert>{error?.message ?? 'Could not load expenses.'}</Alert>
        </div>
      ) : data.content.length === 0 ? (
        <EmptyState
          title="No shared expenses yet"
          description="Add what someone paid for the group and choose how to split it. Balances update automatically."
        />
      ) : (
        <>
          <ul className={isPlaceholderData ? 'divide-y divide-slate-100 opacity-60' : 'divide-y divide-slate-100'}>
            {data.content.map((expense) => {
              const isOpen = expanded === expense.id
              const myShare = expense.shares.find((s) => s.userId === user?.id)
              return (
                <li key={expense.id}>
                  <button
                    type="button"
                    aria-expanded={isOpen}
                    onClick={() => setExpanded(isOpen ? null : expense.id)}
                    className="flex w-full items-center gap-4 px-6 py-3 text-left hover:bg-slate-50"
                  >
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium">{expense.description}</p>
                      <p className="truncate text-xs text-slate-500">
                        {formatDate(expense.expenseDate)} ·{' '}
                        {expense.paidBy.userId === user?.id ? 'You' : expense.paidBy.displayName} paid{' '}
                        {formatMoney(expense.amount, currency)}
                      </p>
                    </div>
                    <div className="text-right text-sm">
                      {myShare ? (
                        <>
                          <p className="tabular-nums">{formatMoney(myShare.owed, currency)}</p>
                          <p className="text-xs text-slate-500">your share</p>
                        </>
                      ) : (
                        <p className="text-xs text-slate-500">not involved</p>
                      )}
                    </div>
                  </button>
                  {isOpen && (
                    <div className="space-y-3 bg-slate-50 px-6 py-3">
                      <p className="text-xs text-slate-500">
                        {formatMoney(expense.amount, currency)} {describeSplit(expense.splitType)}
                        {expense.createdBy && ` · added by ${expense.createdBy.displayName}`}
                      </p>
                      <ul className="space-y-1 text-sm">
                        {expense.shares.map((share) => (
                          <li key={share.userId} className="flex justify-between gap-3">
                            <span className="truncate">
                              {share.displayName}
                              {share.userId === user?.id && <span className="text-slate-500"> (you)</span>}
                              <span className="text-slate-500">{shareInput(expense.splitType, share, currency)}</span>
                            </span>
                            <span className="tabular-nums">{formatMoney(share.owed, currency)}</span>
                          </li>
                        ))}
                      </ul>
                      {expense.canEdit && (
                        <div className="flex justify-end gap-2">
                          <Button variant="secondary" className="px-3 py-1" onClick={() => onEdit(expense)}>
                            Edit
                          </Button>
                          <Button
                            variant="ghost"
                            className="px-3 py-1 text-red-600"
                            onClick={() => setDeleting(expense)}
                          >
                            Delete
                          </Button>
                        </div>
                      )}
                    </div>
                  )}
                </li>
              )
            })}
          </ul>
          {data.totalPages > 1 && (
            <div className="flex items-center justify-between border-t border-slate-100 px-6 py-3 text-sm">
              <Button variant="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>
                Previous
              </Button>
              <span className="text-slate-500">
                Page {page + 1} of {data.totalPages}
              </span>
              <Button variant="secondary" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>
                Next
              </Button>
            </div>
          )}
        </>
      )}

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
        <strong className="text-slate-900">{deleting?.description}</strong> will be removed and everyone's balances
        updated.
      </ConfirmDialog>
    </section>
  )
}

/** What was entered for a person, e.g. " (25%)" or " (2 shares)". Empty for equal splits. */
function shareInput(splitType: SplitType, share: ExpenseShare, currency: string) {
  if (share.value === null) return ''
  switch (splitType) {
    case 'PERCENT':
      return ` (${share.value}%)`
    case 'SHARES':
      return ` (${share.value} ${share.value === 1 ? 'share' : 'shares'})`
    case 'EXACT':
      return ` (${formatMoney(share.value, currency)})`
    default:
      return ''
  }
}
