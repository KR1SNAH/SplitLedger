import { useAuth } from '@/auth/useAuth'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { formatMoney } from '@/lib/format'
import type { GroupDetail, MemberBalance, SuggestedPayment } from '@/lib/types'
import { useGroupBalances } from './api'
import type { SettleUpDraft } from './SettleUpDialog'

interface BalancesPanelProps {
  group: GroupDetail
  onSettle: (draft: SettleUpDraft) => void
}

export function BalancesPanel({ group, onSettle }: BalancesPanelProps) {
  const { user } = useAuth()
  const { data, isPending, error } = useGroupBalances(group.id)
  const isOwner = group.myRole === 'OWNER'

  const canRecord = (payment: SuggestedPayment) =>
    isOwner || payment.from.userId === user?.id || payment.to.userId === user?.id

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-6">
      <h2 className="font-semibold">Balances</h2>
      {isPending ? (
        <p className="mt-3 text-sm text-slate-500">Loading…</p>
      ) : !data ? (
        <div className="mt-3">
          <Alert>{error?.message ?? 'Could not load balances.'}</Alert>
        </div>
      ) : (
        <>
          <ul className="mt-3 divide-y divide-slate-100">
            {data.members.map((balance) => (
              <li key={balance.user.userId} className="flex items-center justify-between gap-3 py-2.5 text-sm">
                <span className="truncate font-medium">
                  {balance.user.displayName}
                  {balance.user.userId === user?.id && <span className="font-normal text-slate-500"> (you)</span>}
                </span>
                <BalanceAmount balance={balance} currency={data.currency} />
              </li>
            ))}
          </ul>

          <h3 className="mt-5 text-sm font-semibold text-slate-700">To settle up</h3>
          {data.suggestedPayments.length === 0 ? (
            <p className="mt-2 text-sm text-slate-500">Everyone is settled up.</p>
          ) : (
            <>
              <p className="mt-1 text-xs text-slate-500">The fewest payments that clear every balance.</p>
              <ul className="mt-2 space-y-2">
                {data.suggestedPayments.map((payment) => (
                  <li
                    key={`${payment.from.userId}-${payment.to.userId}`}
                    className="flex items-center justify-between gap-3 rounded-lg bg-slate-50 px-3 py-2 text-sm"
                  >
                    <span className="min-w-0">
                      <span className="font-medium">{youOr(payment.from.userId, payment.from.displayName, user?.id)}</span>
                      <span className="text-slate-500"> pays </span>
                      <span className="font-medium">{youOr(payment.to.userId, payment.to.displayName, user?.id)}</span>{' '}
                      <span className="tabular-nums">{formatMoney(payment.amount, data.currency)}</span>
                    </span>
                    {canRecord(payment) && (
                      <Button
                        variant="secondary"
                        className="shrink-0 px-2.5 py-1 text-xs"
                        onClick={() =>
                          onSettle({ fromUserId: payment.from.userId, toUserId: payment.to.userId, amount: payment.amount })
                        }
                      >
                        Record
                      </Button>
                    )}
                  </li>
                ))}
              </ul>
            </>
          )}
        </>
      )}
    </section>
  )
}

function youOr(userId: string, name: string, myId: string | undefined) {
  return userId === myId ? 'You' : name
}

/** Net is positive when the group owes this person, negative when they owe the group. */
function BalanceAmount({ balance, currency }: { balance: MemberBalance; currency: string }) {
  if (balance.net > 0) {
    return <span className="text-emerald-700">gets back {formatMoney(balance.net, currency)}</span>
  }
  if (balance.net < 0) {
    return <span className="text-red-600">owes {formatMoney(-balance.net, currency)}</span>
  }
  return <span className="text-slate-500">settled up</span>
}
