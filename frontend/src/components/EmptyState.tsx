import type { ReactNode } from 'react'

export function EmptyState({ title, description, action }: { title: string; description: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-2 px-4 py-12 text-center">
      <p className="font-medium">{title}</p>
      <p className="max-w-sm text-sm text-slate-600">{description}</p>
      {action && <div className="mt-2">{action}</div>}
    </div>
  )
}
