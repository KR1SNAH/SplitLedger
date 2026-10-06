import type { Category } from '@/lib/types'

export function CategoryBadge({ category }: { category: Pick<Category, 'name' | 'color'> | null }) {
  return (
    <span className="inline-flex items-center gap-1.5 whitespace-nowrap rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-700">
      <span className="size-2 rounded-full" style={{ backgroundColor: category?.color ?? '#cbd5e1' }} aria-hidden />
      {category?.name ?? 'Uncategorized'}
    </span>
  )
}
