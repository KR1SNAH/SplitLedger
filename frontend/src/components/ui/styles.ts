import { cn } from '@/lib/cn'

export const inputClass = (error?: string) =>
  cn(
    'block w-full rounded-lg border bg-white px-3 py-2 text-sm shadow-xs outline-none transition',
    'placeholder:text-slate-400 focus:ring-2',
    error
      ? 'border-red-400 focus:border-red-500 focus:ring-red-100'
      : 'border-slate-300 focus:border-emerald-500 focus:ring-emerald-100',
  )
