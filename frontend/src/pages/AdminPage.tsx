import { useState, type ReactNode } from 'react'
import { useAuth } from '@/auth/useAuth'
import { ColumnChart } from '@/components/charts/ColumnChart'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { useAdminStats, useAdminUsers, useSetUserEnabled } from '@/features/admin/api'
import { cn } from '@/lib/cn'
import { formatInstant, formatMonth } from '@/lib/format'
import type { AdminStats, User } from '@/lib/types'

const numberFormat = new Intl.NumberFormat()

export function AdminPage() {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Admin</h1>
        <p className="mt-1 text-slate-600">Platform activity and user accounts.</p>
      </div>
      <StatsSection />
      <UsersSection />
    </div>
  )
}

function StatsSection() {
  const { data: stats, isError, error } = useAdminStats()
  if (isError) return <Alert>{error.message}</Alert>

  const n = (value: number | undefined) => (value === undefined ? '—' : numberFormat.format(value))
  const totalExpenses = stats ? stats.content.personalExpenses + stats.content.groupExpenses : undefined

  return (
    <>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Tile label="Users" value={n(stats?.users.total)}>
          {stats && `${n(stats.users.admins)} admin · ${n(stats.users.disabled)} disabled`}
        </Tile>
        <Tile label="Active in the last 30 days" value={n(stats?.users.activeLast30Days)}>
          {stats && `${n(stats.users.newLast30Days)} new sign-ups`}
        </Tile>
        <Tile label="Groups" value={n(stats?.content.groups)}>
          {stats && `${n(stats.content.settlements)} payments recorded`}
        </Tile>
        <Tile label="Expenses logged" value={n(totalExpenses)}>
          {stats && `${n(stats.content.personalExpenses)} personal · ${n(stats.content.groupExpenses)} in groups`}
        </Tile>
      </div>

      {/* Two measures on different scales: two charts, never one chart with two axes. */}
      <div className="grid gap-4 lg:grid-cols-2">
        <ChartCard title="Sign-ups per month">
          {stats && <MonthlyChart stats={stats} measure="newUsers" title="New users per month" />}
        </ChartCard>
        <ChartCard title="Expenses added per month">
          {stats && <MonthlyChart stats={stats} measure="newExpenses" title="Expenses added per month" />}
        </ChartCard>
      </div>
    </>
  )
}

function Tile({ label, value, children }: { label: string; value: string; children: ReactNode }) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-5">
      <p className="text-sm text-slate-600">{label}</p>
      <p className="mt-2 text-3xl font-semibold">{value}</p>
      <p className="mt-1 text-sm text-slate-500">{children ?? ' '}</p>
    </div>
  )
}

function ChartCard({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5">
      <h2 className="font-semibold">{title}</h2>
      <p className="text-xs text-slate-500">Last 6 months, UTC</p>
      <div className="mt-4 min-h-52">{children}</div>
    </section>
  )
}

function MonthlyChart({
  stats,
  measure,
  title,
}: {
  stats: AdminStats
  measure: 'newUsers' | 'newExpenses'
  title: string
}) {
  return (
    <ColumnChart
      title={title}
      columns={stats.monthly.map((m) => ({
        key: m.month,
        label: formatMonth(m.month, 'short'),
        fullLabel: formatMonth(m.month),
        value: m[measure],
        valueLabel: numberFormat.format(m[measure]),
      }))}
    />
  )
}

function UsersSection() {
  const { user: me } = useAuth()
  const [page, setPage] = useState(0)
  const { data, isPending, isError, error } = useAdminUsers(page)
  const setEnabled = useSetUserEnabled()
  const [disabling, setDisabling] = useState<User | null>(null)

  async function confirmDisable() {
    if (!disabling) return
    try {
      await setEnabled.mutateAsync({ id: disabling.id, enabled: false })
      setDisabling(null)
    } catch {
      // Shown in the dialog via setEnabled.error.
    }
  }

  return (
    <section className="space-y-4">
      <div>
        <h2 className="text-lg font-semibold">Users</h2>
        <p className="text-sm text-slate-600">{data ? `${data.totalElements} registered accounts` : 'Loading…'}</p>
      </div>

      {isError && <Alert>{error.message}</Alert>}
      {setEnabled.isError && !disabling && <Alert>{setEnabled.error.message}</Alert>}

      <div className="overflow-x-auto rounded-2xl border border-slate-200 bg-white">
        <table className="min-w-full divide-y divide-slate-200 text-sm">
          <thead className="bg-slate-50 text-left text-slate-600">
            <tr>
              <th className="px-4 py-3 font-medium">Name</th>
              <th className="px-4 py-3 font-medium">Email</th>
              <th className="px-4 py-3 font-medium">Role</th>
              <th className="px-4 py-3 font-medium">Status</th>
              <th className="px-4 py-3 font-medium">Joined</th>
              <th className="px-4 py-3">
                <span className="sr-only">Actions</span>
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {isPending && (
              <tr>
                <td colSpan={6} className="px-4 py-6 text-center text-slate-500">
                  Loading…
                </td>
              </tr>
            )}
            {data?.content.map((u) => (
              <tr key={u.id} className={cn(!u.enabled && 'bg-slate-50/60')}>
                <td className="px-4 py-3 font-medium">
                  {u.displayName}
                  {u.id === me?.id && <span className="ml-1.5 text-xs font-normal text-slate-500">(you)</span>}
                </td>
                <td className="px-4 py-3 text-slate-600">{u.email}</td>
                <td className="px-4 py-3">
                  <Pill className={u.role === 'ADMIN' ? 'bg-violet-100 text-violet-700' : 'bg-slate-100 text-slate-700'}>
                    {u.role}
                  </Pill>
                </td>
                <td className="px-4 py-3">
                  <Pill className={u.enabled ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-700'}>
                    {u.enabled ? 'Active' : 'Disabled'}
                  </Pill>
                </td>
                <td className="px-4 py-3 text-slate-600">{formatInstant(u.createdAt)}</td>
                <td className="px-4 py-3 text-right">
                  {/* Admins can't disable themselves (the API refuses too). */}
                  {u.id !== me?.id &&
                    (u.enabled ? (
                      <Button variant="ghost" className="px-2 py-1 text-red-600" onClick={() => setDisabling(u)}>
                        Disable
                      </Button>
                    ) : (
                      <Button
                        variant="ghost"
                        className="px-2 py-1"
                        loading={setEnabled.isPending && setEnabled.variables?.id === u.id}
                        onClick={() => setEnabled.mutate({ id: u.id, enabled: true })}
                      >
                        Enable
                      </Button>
                    ))}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-end gap-2">
          <Button variant="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
            Previous
          </Button>
          <span className="text-sm text-slate-600">
            Page {page + 1} of {data.totalPages}
          </span>
          <Button variant="secondary" disabled={page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)}>
            Next
          </Button>
        </div>
      )}

      <ConfirmDialog
        open={disabling !== null}
        title="Disable account?"
        confirmLabel="Disable"
        onConfirm={confirmDisable}
        onClose={() => {
          setDisabling(null)
          setEnabled.reset()
        }}
        pending={setEnabled.isPending}
        error={setEnabled.error?.message}
      >
        <strong className="text-slate-900">{disabling?.displayName}</strong> ({disabling?.email}) will be signed out
        everywhere right away and won't be able to sign in until you enable the account again. Their data is kept.
      </ConfirmDialog>
    </section>
  )
}

function Pill({ className, children }: { className: string; children: ReactNode }) {
  return <span className={cn('rounded-full px-2 py-0.5 text-xs font-medium', className)}>{children}</span>
}
