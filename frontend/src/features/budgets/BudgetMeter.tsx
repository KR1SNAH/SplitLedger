import type { ReactNode } from 'react'
import { CategoryBadge } from '@/components/CategoryBadge'
import { cn } from '@/lib/cn'
import { formatMoney } from '@/lib/format'
import type { BudgetStatus } from '@/lib/types'

/** Past this share of the limit a budget shows as "nearly spent". */
const WARNING_PERCENT = 80

const levels = {
  ok: { track: 'bg-emerald-100', fill: 'bg-emerald-600' },
  warning: { track: 'bg-amber-100', fill: 'bg-amber-500' },
  over: { track: 'bg-red-100', fill: 'bg-red-600' },
}

interface BudgetMeterProps {
  budget: BudgetStatus
  currency: string
  /** Buttons shown on the right, e.g. edit and delete. */
  actions?: ReactNode
}

/** One budget: what's been spent against the limit, as a bar plus words (never color alone). */
export function BudgetMeter({ budget, currency, actions }: BudgetMeterProps) {
  const level = budget.overBudget ? 'over' : budget.percentUsed >= WARNING_PERCENT ? 'warning' : 'ok'
  const name = budget.category?.name ?? 'Overall'

  return (
    <div>
      <div className="flex items-center justify-between gap-3">
        {budget.category ? (
          <CategoryBadge category={budget.category} />
        ) : (
          <span className="text-sm font-medium">Overall (all spending)</span>
        )}
        <div className="flex items-center gap-2">
          <span className="text-sm tabular-nums">
            <span className="font-medium">{formatMoney(budget.spent, currency)}</span>
            <span className="text-slate-500"> of {formatMoney(budget.amount, currency)}</span>
          </span>
          {actions}
        </div>
      </div>
      <div
        role="progressbar"
        aria-label={`${name} budget used`}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={Math.min(budget.percentUsed, 100)}
        aria-valuetext={`${budget.percentUsed}% used`}
        className={cn('mt-2 h-2 rounded-full', levels[level].track)}
      >
        <div
          className={cn('h-2 rounded-full', levels[level].fill)}
          style={{ width: `${Math.min(budget.percentUsed, 100)}%` }}
        />
      </div>
      <p className="mt-1.5 flex justify-between text-xs">
        {budget.overBudget ? (
          <span className="inline-flex items-center gap-1 font-medium text-red-700">
            <svg viewBox="0 0 16 16" fill="currentColor" className="size-3.5" aria-hidden>
              <path d="M8 1.5a1 1 0 0 1 .87.5l6 10.5A1 1 0 0 1 14 14H2a1 1 0 0 1-.87-1.5l6-10.5A1 1 0 0 1 8 1.5Zm0 4a.75.75 0 0 0-.75.75v3a.75.75 0 0 0 1.5 0v-3A.75.75 0 0 0 8 5.5Zm0 6.75a.875.875 0 1 0 0-1.75.875.875 0 0 0 0 1.75Z" />
            </svg>
            Over by {formatMoney(-budget.remaining, currency)}
          </span>
        ) : (
          <span className={cn(level === 'warning' ? 'font-medium text-amber-700' : 'text-slate-600')}>
            {formatMoney(budget.remaining, currency)} left
          </span>
        )}
        <span className="text-slate-500 tabular-nums">{budget.percentUsed}%</span>
      </p>
    </div>
  )
}
