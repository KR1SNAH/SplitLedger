import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { EmptyState } from '@/components/EmptyState'
import { RoleBadge } from '@/components/RoleBadge'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { useGroups, useMyInvites, useRespondToInvite } from '@/features/groups/api'
import { GroupFormDialog } from '@/features/groups/GroupFormDialog'
import type { MyInvite } from '@/lib/types'

export function GroupsPage() {
  const groups = useGroups()
  const navigate = useNavigate()
  const [formOpen, setFormOpen] = useState(false)

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Groups</h1>
          <p className="mt-1 text-slate-600">Share expenses with housemates, trips and friends.</p>
        </div>
        <Button onClick={() => setFormOpen(true)}>+ New group</Button>
      </div>

      <PendingInvites />

      {groups.isError && <Alert>{groups.error.message}</Alert>}

      {groups.isPending ? (
        <p className="text-sm text-slate-500">Loading…</p>
      ) : groups.data?.length === 0 ? (
        <div className="rounded-2xl border border-slate-200 bg-white">
          <EmptyState
            title="No groups yet"
            description="Create a group, then invite people with a link or by email."
            action={<Button onClick={() => setFormOpen(true)}>Create a group</Button>}
          />
        </div>
      ) : (
        <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {groups.data?.map((group) => (
            <li key={group.id}>
              <Link
                to={`/groups/${group.id}`}
                className="flex h-full flex-col rounded-2xl border border-slate-200 bg-white p-5 transition hover:border-emerald-300 hover:shadow-sm"
              >
                <div className="flex items-start justify-between gap-3">
                  <h2 className="font-semibold">{group.name}</h2>
                  {group.myRole === 'OWNER' && <RoleBadge role="OWNER" />}
                </div>
                {group.description && (
                  <p className="mt-1 line-clamp-2 text-sm text-slate-600">{group.description}</p>
                )}
                <p className="mt-auto pt-4 text-sm text-slate-500">
                  {group.memberCount} {group.memberCount === 1 ? 'member' : 'members'} · {group.currency}
                </p>
              </Link>
            </li>
          ))}
        </ul>
      )}

      <GroupFormDialog
        open={formOpen}
        onClose={() => setFormOpen(false)}
        onSaved={(group) => navigate(`/groups/${group.id}`)}
      />
    </div>
  )
}

function PendingInvites() {
  const { data: invites = [] } = useMyInvites()
  const respond = useRespondToInvite()
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)

  if (invites.length === 0) return null

  async function handle(invite: MyInvite, accept: boolean) {
    setError(null)
    try {
      await respond.mutateAsync({ id: invite.id, accept })
      if (accept) navigate(`/groups/${invite.groupId}`)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong. Please try again.')
    }
  }

  return (
    <section className="rounded-2xl border border-emerald-200 bg-emerald-50/60 p-5">
      <h2 className="font-semibold">Invitations</h2>
      {error && (
        <div className="mt-3">
          <Alert>{error}</Alert>
        </div>
      )}
      <ul className="mt-3 divide-y divide-emerald-100">
        {invites.map((invite) => {
          const busy = respond.isPending && respond.variables?.id === invite.id
          return (
            <li key={invite.id} className="flex flex-wrap items-center justify-between gap-3 py-2">
              <p className="text-sm">
                <span className="font-medium">{invite.invitedBy ?? 'Someone'}</span> invited you to{' '}
                <span className="font-medium">{invite.groupName}</span>
              </p>
              <div className="flex gap-2">
                <Button variant="ghost" disabled={busy} onClick={() => handle(invite, false)}>
                  Decline
                </Button>
                <Button loading={busy && respond.variables?.accept} disabled={busy} onClick={() => handle(invite, true)}>
                  Join
                </Button>
              </div>
            </li>
          )
        })}
      </ul>
    </section>
  )
}
