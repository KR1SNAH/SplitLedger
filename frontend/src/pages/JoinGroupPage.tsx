import { Link, useNavigate, useParams } from 'react-router'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { useInviteLinkPreview, useJoinByLink } from '@/features/groups/api'
import { ApiError } from '@/lib/api'

/** Landing page for /join/:token invite links. Signed-out visitors are sent to log in and brought back here. */
export function JoinGroupPage() {
  const { token = '' } = useParams()
  const navigate = useNavigate()
  const preview = useInviteLinkPreview(token)
  const join = useJoinByLink()

  async function handleJoin() {
    try {
      const group = await join.mutateAsync(token)
      navigate(`/groups/${group.id}`, { replace: true })
    } catch {
      // Shown via join.error.
    }
  }

  return (
    <div className="mx-auto max-w-md rounded-2xl border border-slate-200 bg-white p-8 text-center">
      {preview.isPending ? (
        <p className="text-sm text-slate-500">Checking invite…</p>
      ) : preview.isError ? (
        <>
          <h1 className="text-lg font-semibold">
            {preview.error instanceof ApiError && preview.error.status === 404
              ? 'This invite link is invalid or has expired'
              : "Couldn't open this invite"}
          </h1>
          <p className="mt-2 text-sm text-slate-600">Ask a group owner to send you a new link.</p>
          <Link to="/groups" className="mt-4 inline-block text-sm font-medium text-emerald-700 hover:underline">
            Go to your groups
          </Link>
        </>
      ) : (
        <>
          <p className="text-sm text-slate-500">You've been invited to join</p>
          <h1 className="mt-1 text-2xl font-semibold tracking-tight">{preview.data.groupName}</h1>
          <p className="mt-1 text-sm text-slate-500">
            {preview.data.memberCount} {preview.data.memberCount === 1 ? 'member' : 'members'}
          </p>
          {join.isError && (
            <div className="mt-4 text-left">
              <Alert>{join.error.message}</Alert>
            </div>
          )}
          <div className="mt-6">
            {preview.data.alreadyMember ? (
              <Button onClick={() => navigate(`/groups/${preview.data.groupId}`, { replace: true })}>
                You're already a member. Open group
              </Button>
            ) : (
              <Button onClick={handleJoin} loading={join.isPending}>
                Join group
              </Button>
            )}
          </div>
        </>
      )}
    </div>
  )
}
