import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { EmptyState } from '@/components/EmptyState'
import { RoleBadge } from '@/components/RoleBadge'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { useDeleteGroup, useGroup, useLeaveGroup } from '@/features/groups/api'
import { GroupFormDialog } from '@/features/groups/GroupFormDialog'
import { InvitePanel } from '@/features/groups/InvitePanel'
import { MemberList } from '@/features/groups/MemberList'
import { BalancesPanel } from '@/features/ledger/BalancesPanel'
import { GroupExpenseFormDialog } from '@/features/ledger/GroupExpenseFormDialog'
import { GroupExpenseList } from '@/features/ledger/GroupExpenseList'
import { SettlementList } from '@/features/ledger/SettlementList'
import { SettleUpDialog, type SettleUpDraft } from '@/features/ledger/SettleUpDialog'
import { ApiError } from '@/lib/api'
import type { GroupExpense } from '@/lib/types'

export function GroupDetailPage() {
  const { groupId = '' } = useParams()
  const navigate = useNavigate()
  const { data: group, isPending, error } = useGroup(groupId)
  const deleteGroup = useDeleteGroup()
  const leaveGroup = useLeaveGroup()
  const [editing, setEditing] = useState(false)
  const [confirming, setConfirming] = useState<'delete' | 'leave' | null>(null)
  // null = closed, 'new' = adding, otherwise the expense being edited.
  const [expenseDialog, setExpenseDialog] = useState<GroupExpense | 'new' | null>(null)
  const [settleUp, setSettleUp] = useState<{ draft: SettleUpDraft | null } | null>(null)

  const backLink = (
    <Link to="/groups" className="text-sm font-medium text-emerald-700 hover:underline">
      ← All groups
    </Link>
  )

  if (isPending) return <p className="text-sm text-slate-500">Loading…</p>

  if (!group) {
    // Non-members get a 404 too, so "not found" covers "not yours".
    return (
      <div className="space-y-4">
        {backLink}
        {error instanceof ApiError && error.status === 404 ? (
          <div className="rounded-2xl border border-slate-200 bg-white">
            <EmptyState
              title="Group not found"
              description="It may have been deleted, or you're not a member of it any more."
            />
          </div>
        ) : (
          <Alert>{error?.message ?? 'Something went wrong.'}</Alert>
        )}
      </div>
    )
  }

  const isOwner = group.myRole === 'OWNER'
  const action = confirming === 'delete' ? deleteGroup : leaveGroup

  async function confirm() {
    try {
      await action.mutateAsync(groupId)
      navigate('/groups', { replace: true })
    } catch {
      // Shown in the dialog (e.g. "You're the only owner").
    }
  }

  return (
    <div className="space-y-6">
      {backLink}

      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-semibold tracking-tight">{group.name}</h1>
            <RoleBadge role={group.myRole} />
          </div>
          {group.description && <p className="mt-1 max-w-2xl text-slate-600">{group.description}</p>}
          <p className="mt-1 text-sm text-slate-500">Amounts in {group.currency}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button variant="secondary" onClick={() => setSettleUp({ draft: null })}>
            Settle up
          </Button>
          {isOwner && (
            <Button variant="secondary" onClick={() => setEditing(true)}>
              Edit
            </Button>
          )}
          <Button variant="secondary" onClick={() => setConfirming('leave')}>
            Leave
          </Button>
          {isOwner && (
            <Button variant="danger" onClick={() => setConfirming('delete')}>
              Delete
            </Button>
          )}
        </div>
      </div>

      <div className="grid items-start gap-6 lg:grid-cols-[3fr_2fr]">
        <div className="space-y-6">
          <GroupExpenseList
            groupId={group.id}
            currency={group.currency}
            onAdd={() => setExpenseDialog('new')}
            onEdit={setExpenseDialog}
          />
          <SettlementList groupId={group.id} currency={group.currency} />
        </div>
        <div className="space-y-6">
          <BalancesPanel group={group} onSettle={(draft) => setSettleUp({ draft })} />
          <MemberList group={group} />
          {isOwner && <InvitePanel group={group} />}
        </div>
      </div>

      <GroupFormDialog open={editing} onClose={() => setEditing(false)} group={group} />
      <GroupExpenseFormDialog
        open={expenseDialog !== null}
        onClose={() => setExpenseDialog(null)}
        group={group}
        expense={expenseDialog === 'new' ? null : expenseDialog}
      />
      <SettleUpDialog
        open={settleUp !== null}
        onClose={() => setSettleUp(null)}
        group={group}
        draft={settleUp?.draft ?? null}
      />

      <ConfirmDialog
        open={confirming !== null}
        title={confirming === 'delete' ? 'Delete group?' : 'Leave group?'}
        confirmLabel={confirming === 'delete' ? 'Delete group' : 'Leave'}
        onConfirm={confirm}
        onClose={() => {
          setConfirming(null)
          deleteGroup.reset()
          leaveGroup.reset()
        }}
        pending={action.isPending}
        error={action.error?.message}
      >
        {confirming === 'delete' ? (
          <>
            <strong className="text-slate-900">{group.name}</strong> will be deleted for all {group.members.length}{' '}
            {group.members.length === 1 ? 'member' : 'members'}, along with its expenses and payments. This can't be
            undone.
          </>
        ) : (
          <>
            You'll lose access to <strong className="text-slate-900">{group.name}</strong>. You'll need a new invite to
            rejoin. You can only leave once you're settled up.
          </>
        )}
      </ConfirmDialog>
    </div>
  )
}
