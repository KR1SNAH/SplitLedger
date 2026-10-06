import { cn } from '@/lib/cn'
import type { GroupRole } from '@/lib/types'

export function RoleBadge({ role }: { role: GroupRole }) {
  return (
    <span
      className={cn(
        'rounded-full px-2 py-0.5 text-xs font-medium',
        role === 'OWNER' ? 'bg-emerald-100 text-emerald-800' : 'bg-slate-100 text-slate-700',
      )}
    >
      {role === 'OWNER' ? 'Owner' : 'Member'}
    </span>
  )
}
