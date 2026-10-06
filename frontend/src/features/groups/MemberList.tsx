import { useState } from 'react'
import { useAuth } from '@/auth/useAuth'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { RoleBadge } from '@/components/RoleBadge'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import type { GroupDetail, GroupMember } from '@/lib/types'
import { useChangeRole, useRemoveMember } from './api'

function initials(name: string) {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]!.toUpperCase())
    .join('')
}

export function MemberList({ group }: { group: GroupDetail }) {
  const { user } = useAuth()
  const changeRole = useChangeRole(group.id)
  const removeMember = useRemoveMember(group.id)
  const [removing, setRemoving] = useState<GroupMember | null>(null)
  const isOwner = group.myRole === 'OWNER'

  async function confirmRemove() {
    if (!removing) return
    try {
      await removeMember.mutateAsync(removing.userId)
      setRemoving(null)
    } catch {
      // Shown in the dialog via removeMember.error.
    }
  }

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-6">
      <h2 className="font-semibold">
        Members <span className="font-normal text-slate-500">({group.members.length})</span>
      </h2>
      {changeRole.isError && (
        <div className="mt-3">
          <Alert>{changeRole.error.message}</Alert>
        </div>
      )}
      <ul className="mt-3 divide-y divide-slate-100">
        {group.members.map((member) => {
          const isMe = member.userId === user?.id
          const busy = changeRole.isPending && changeRole.variables?.userId === member.userId
          return (
            <li key={member.userId} className="flex flex-wrap items-center gap-3 py-3">
              <span
                aria-hidden
                className="flex size-9 shrink-0 items-center justify-center rounded-full bg-slate-100 text-sm font-medium text-slate-600"
              >
                {initials(member.displayName)}
              </span>
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium">
                  {member.displayName}
                  {isMe && <span className="font-normal text-slate-500"> (you)</span>}
                </p>
                <p className="truncate text-sm text-slate-500">{member.email}</p>
              </div>
              <RoleBadge role={member.role} />
              {isOwner && (
                <div className="flex gap-1">
                  <Button
                    variant="ghost"
                    className="px-2 py-1"
                    loading={busy}
                    onClick={() =>
                      changeRole.mutate({
                        userId: member.userId,
                        role: member.role === 'OWNER' ? 'MEMBER' : 'OWNER',
                      })
                    }
                  >
                    {member.role === 'OWNER' ? (isMe ? 'Step down' : 'Make member') : 'Make owner'}
                  </Button>
                  {!isMe && (
                    <Button variant="ghost" className="px-2 py-1 text-red-600" onClick={() => setRemoving(member)}>
                      Remove
                    </Button>
                  )}
                </div>
              )}
            </li>
          )
        })}
      </ul>

      <ConfirmDialog
        open={removing !== null}
        title="Remove member?"
        confirmLabel="Remove"
        onConfirm={confirmRemove}
        onClose={() => {
          setRemoving(null)
          removeMember.reset()
        }}
        pending={removeMember.isPending}
        error={removeMember.error?.message}
      >
        <strong className="text-slate-900">{removing?.displayName}</strong> will lose access to {group.name}.
      </ConfirmDialog>
    </section>
  )
}
