import { Navigate, Outlet, useLocation } from 'react-router'
import type { Role } from '@/lib/types'
import { useAuth } from './useAuth'

export function FullPageSpinner() {
  return (
    <div className="flex min-h-screen items-center justify-center">
      <div className="size-8 animate-spin rounded-full border-4 border-emerald-600 border-t-transparent" />
    </div>
  )
}

/** Renders child routes only for signed-in users (optionally with a given role). */
export function RequireAuth({ role }: { role?: Role }) {
  const { user, isLoading } = useAuth()
  const location = useLocation()

  if (isLoading) return <FullPageSpinner />
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  if (role && user.role !== role) return <Navigate to="/" replace />
  return <Outlet />
}

/**
 * Keeps signed-in users away from the login/register pages. This is also what redirects after a
 * successful login: once the user is set, it sends them back to the page they originally wanted.
 */
export function GuestOnly() {
  const { user, isLoading } = useAuth()
  const location = useLocation()

  if (isLoading) return <FullPageSpinner />
  if (user) {
    const from = (location.state as { from?: string } | null)?.from ?? '/'
    return <Navigate to={from} replace />
  }
  return <Outlet />
}
