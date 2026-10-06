import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { inputClass } from '@/components/ui/styles'
import { TextField } from '@/components/ui/TextField'
import { ApiError } from '@/lib/api'
import { formatInstant } from '@/lib/format'
import { applyServerErrors } from '@/lib/formErrors'
import type { CreatedInviteLink, GroupDetail } from '@/lib/types'
import { useCancelInvite, useCreateInviteLink, useDisableInviteLink, useGroupInvites, useInviteByEmail } from './api'

/** Owner-only: the shareable invite link and email invites. */
export function InvitePanel({ group }: { group: GroupDetail }) {
  return (
    <section className="space-y-6 rounded-2xl border border-slate-200 bg-white p-6">
      <div>
        <h2 className="font-semibold">Invite people</h2>
        <p className="mt-1 text-sm text-slate-600">Share a link, or invite someone by the email they sign up with.</p>
      </div>
      <InviteLinkSection group={group} />
      <EmailInviteSection groupId={group.id} />
    </section>
  )
}

function InviteLinkSection({ group }: { group: GroupDetail }) {
  const createLink = useCreateInviteLink(group.id)
  const disableLink = useDisableInviteLink(group.id)
  // The server only stores a hash, so the link can only be shown right after it's created.
  const [created, setCreated] = useState<CreatedInviteLink | null>(null)
  const [copied, setCopied] = useState(false)

  const url = created ? `${window.location.origin}/join/${created.token}` : null
  const error = createLink.error ?? disableLink.error

  async function create() {
    setCopied(false)
    try {
      setCreated(await createLink.mutateAsync())
    } catch {
      // Shown via createLink.error.
    }
  }

  async function disable() {
    try {
      await disableLink.mutateAsync()
      setCreated(null)
    } catch {
      // Shown via disableLink.error.
    }
  }

  async function copy() {
    if (!url) return
    try {
      await navigator.clipboard.writeText(url)
      setCopied(true)
    } catch {
      // Clipboard access can be blocked; the field is selectable as a fallback.
    }
  }

  return (
    <div className="space-y-3">
      <h3 className="text-sm font-medium text-slate-700">Invite link</h3>
      {error && <Alert>{error.message}</Alert>}

      {url && created ? (
        <>
          <div className="flex gap-2">
            <input
              readOnly
              value={url}
              aria-label="Invite link"
              onFocus={(e) => e.target.select()}
              className={inputClass()}
            />
            <Button variant="secondary" onClick={copy} className="shrink-0">
              {copied ? 'Copied' : 'Copy'}
            </Button>
          </div>
          <p className="text-sm text-slate-600">
            Copy it now: for security the link is only shown once. Anyone signed in with it can join until{' '}
            {formatInstant(created.expiresAt)}.
          </p>
        </>
      ) : group.inviteLink ? (
        <p className="text-sm text-slate-600">
          A link is active until {formatInstant(group.inviteLink.expiresAt)}. Links are only shown when created, so to
          share it again, create a new one (the current link will stop working).
        </p>
      ) : (
        <p className="text-sm text-slate-600">No active link.</p>
      )}

      <div className="flex gap-2">
        <Button variant="secondary" onClick={create} loading={createLink.isPending}>
          {group.inviteLink ? 'Create new link' : 'Create invite link'}
        </Button>
        {group.inviteLink && (
          <Button variant="ghost" className="text-red-600" onClick={disable} loading={disableLink.isPending}>
            Disable link
          </Button>
        )}
      </div>
    </div>
  )
}

const emailSchema = z.object({ email: z.email('Enter a valid email').max(255) })

function EmailInviteSection({ groupId }: { groupId: string }) {
  const invites = useGroupInvites(groupId)
  const inviteByEmail = useInviteByEmail(groupId)
  const cancelInvite = useCancelInvite(groupId)
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<z.infer<typeof emailSchema>>({ resolver: zodResolver(emailSchema), defaultValues: { email: '' } })

  const onSubmit = handleSubmit(async ({ email }) => {
    setFormError(null)
    try {
      await inviteByEmail.mutateAsync(email)
      reset()
    } catch (error) {
      // "Already a member" / "already invited" belong next to the field.
      if (error instanceof ApiError && error.status === 409) {
        setError('email', { type: 'server', message: error.message })
      } else {
        setFormError(applyServerErrors(error, setError))
      }
    }
  })

  return (
    <div className="space-y-3 border-t border-slate-100 pt-6">
      <h3 className="text-sm font-medium text-slate-700">Invite by email</h3>
      {formError && <Alert>{formError}</Alert>}
      <form onSubmit={onSubmit} noValidate className="flex items-start gap-2">
        <div className="flex-1">
          <TextField
            label="Email"
            type="email"
            placeholder="friend@example.com"
            error={errors.email?.message}
            {...register('email')}
          />
        </div>
        <Button type="submit" loading={isSubmitting} className="mt-6.5 shrink-0">
          Invite
        </Button>
      </form>
      <p className="text-xs text-slate-500">
        They'll see the invite on their Groups page once they sign in (or sign up) with this email.
      </p>

      {cancelInvite.isError && <Alert>{cancelInvite.error.message}</Alert>}
      {invites.data && invites.data.length > 0 && (
        <ul className="divide-y divide-slate-100">
          {invites.data.map((invite) => (
            <li key={invite.id} className="flex items-center justify-between gap-3 py-2 text-sm">
              <div className="min-w-0">
                <p className="truncate">{invite.email}</p>
                <p className="text-xs text-slate-500">
                  Invited {formatInstant(invite.createdAt)}
                  {invite.invitedBy && ` by ${invite.invitedBy}`}
                </p>
              </div>
              <Button
                variant="ghost"
                className="px-2 py-1"
                loading={cancelInvite.isPending && cancelInvite.variables === invite.id}
                onClick={() => cancelInvite.mutate(invite.id)}
              >
                Cancel
              </Button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
