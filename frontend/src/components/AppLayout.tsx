import { useState } from 'react'
import { Link, NavLink, Outlet, useNavigate } from 'react-router'
import { useAuth } from '@/auth/useAuth'
import { cn } from '@/lib/cn'
import { Logo } from './Logo'
import { Button } from './ui/Button'

export function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [loggingOut, setLoggingOut] = useState(false)

  const links = [
    { to: '/', label: 'Dashboard', end: true },
    { to: '/expenses', label: 'Expenses', end: false },
    { to: '/budgets', label: 'Budgets', end: false },
    { to: '/groups', label: 'Groups', end: false },
    { to: '/settings', label: 'Settings', end: false },
    ...(user?.role === 'ADMIN' ? [{ to: '/admin', label: 'Admin', end: false }] : []),
  ]

  async function handleLogout() {
    setLoggingOut(true)
    try {
      await logout()
    } finally {
      navigate('/login', { replace: true })
    }
  }

  return (
    <div className="min-h-screen">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex h-16 max-w-6xl items-center gap-6 px-4">
          <Link to="/" aria-label="SplitLedger home">
            <Logo />
          </Link>
          <nav className="flex gap-1">
            {links.map((link) => (
              <NavLink
                key={link.to}
                to={link.to}
                end={link.end}
                className={({ isActive }) =>
                  cn(
                    'rounded-lg px-3 py-2 text-sm font-medium',
                    isActive ? 'bg-emerald-50 text-emerald-700' : 'text-slate-600 hover:text-slate-900',
                  )
                }
              >
                {link.label}
              </NavLink>
            ))}
          </nav>
          <div className="ml-auto flex items-center gap-3">
            <span className="hidden text-sm text-slate-600 sm:inline">{user?.displayName}</span>
            <Button variant="secondary" onClick={handleLogout} loading={loggingOut}>
              Log out
            </Button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  )
}
