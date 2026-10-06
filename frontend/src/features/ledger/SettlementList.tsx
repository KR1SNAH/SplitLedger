import { useState } from 'react'
import { useAuth } from '@/auth/useAuth'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { formatDate, formatMoney } from '@/lib/format'
import type { Settlement } from '@/lib/types'
import { useDeleteSettlement, useSettlements } from './api'

export function SettlementList({ groupId, currency }: { groupId: string; currency: string }) {
  const { user } = useAuth()
  const { data, isPending, error } = useSettlements(groupId)
  const deleteSettlement = useDeleteSettlement(groupId)
  const [deleting, setDeleting] = useState<Settlement | null>(null)

  const nameOf = (ref: { userId: string; displayName: string }) => (ref.userId === user?.id ? 'You' : ref.displayName)

  async function confirmDelete() {
    if (!deleting) return
    try {
      await deleteSettlement.mutateAsync(deleting.id)
      setDeleting(null)
    } catch {
      // Shown in the dialog (e.g. it would reopen a balance for someone who left).
    }
  }

  // Nothing to show until someone records a payment.
  if (isPending || (data && data.length === 0)) return null

  return (
    <section className="rounded-2xl border border-slate-200 bg-white">
      <h2 className="border-b border-slate-100 px-6 py-4 font-semibold">Payments</h2>
      {!data ? (
        <div className="p-6">
          <Alert>{error?.message ?? 'Could not load payments.'}</Alert>
        </div>
      ) : (
        <ul className="divide-y divide-slate-100">
          {data.map((settlement) => (
            <li key={settlement.id} className="flex items-center gap-4 px-6 py-3">
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm">
                  <span className="font-medium">{nameOf(settlement.from)}</span>
                  <span className="text-slate-500"> paid </span>
                  <span className="font-medium">{nameOf(settlement.to)}</span>
                </p>
                <p className="truncate text-xs text-slate-500">
                  {formatDate(settlement.settledOn)}
                  {settlement.note && ` · ${settlement.note}`}
                </p>
              </div>
              <span className="text-sm tabular-nums">{formatMoney(settlement.amount, currency)}</span>
              {settlement.canDelete && (
                <Button
                  variant="ghost"
                  className="px-2 py-1 text-red-600"
                  aria-label="Delete payment"
                  onClick={() => setDeleting(settlement)}
                >
                  Delete
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}

      <ConfirmDialog
        open={deleting !== null}
        title="Delete payment?"
        confirmLabel="Delete"
        onConfirm={confirmDelete}
        onClose={() => {
          setDeleting(null)
          deleteSettlement.reset()
        }}
        pending={deleteSettlement.isPending}
        error={deleteSettlement.error?.message}
      >
        The payment from <strong className="text-slate-900">{deleting && nameOf(deleting.from)}</strong> to{' '}
        <strong className="text-slate-900">{deleting && nameOf(deleting.to)}</strong> will be removed and balances
        updated.
      </ConfirmDialog>
    </section>
  )
}
