import { cn } from '@/lib/cn'

export interface Column {
  key: string
  /** Axis label under the column, e.g. "Sep". */
  label: string
  /** Full name for the tooltip and screen readers, e.g. "September 2026". */
  fullLabel: string
  value: number
  /** How the value is shown, e.g. "$1,204.50" or "12". */
  valueLabel: string
}

interface ColumnChartProps {
  /** Names the chart for screen readers, e.g. "Spending per month". */
  title: string
  columns: Column[]
  /** Highlights the last column (the current period) and labels its value. */
  highlightLast?: boolean
}

/**
 * A single-series column chart in plain HTML: no chart library. Column heights are only a display
 * ratio; every number shown comes formatted from the caller. Each column is a focusable list item
 * labelled with its value, and shows a tooltip on hover or keyboard focus.
 */
export function ColumnChart({ title, columns, highlightLast = true }: ColumnChartProps) {
  const max = Math.max(0, ...columns.map((c) => c.value))

  return (
    <figure>
      <figcaption className="sr-only">{title}</figcaption>
      <ul className="flex h-44 items-end gap-2 border-b border-slate-200 pt-6">
        {columns.map((column, i) => {
          const current = highlightLast && i === columns.length - 1
          const percent = max > 0 ? (column.value / max) * 100 : 0
          return (
            <li
              key={column.key}
              tabIndex={0}
              aria-label={`${column.fullLabel}: ${column.valueLabel}`}
              className="group relative flex h-full flex-1 items-end justify-center outline-none"
            >
              {current && column.value > 0 && (
                <span
                  aria-hidden
                  className="absolute left-1/2 -translate-x-1/2 whitespace-nowrap text-xs font-medium text-slate-700 group-hover:invisible group-focus-visible:invisible"
                  style={{ bottom: `calc(${percent}% + 4px)` }}
                >
                  {column.valueLabel}
                </span>
              )}
              <div
                className={cn(
                  'w-full max-w-6 rounded-t transition-opacity group-hover:opacity-80',
                  'group-focus-visible:ring-2 group-focus-visible:ring-emerald-300 group-focus-visible:ring-offset-2',
                  current ? 'bg-emerald-600' : 'bg-emerald-300',
                )}
                // Non-zero values stay visible as a sliver.
                style={{ height: column.value > 0 ? `max(${percent}%, 2px)` : 0 }}
              />
              <div
                aria-hidden
                className="pointer-events-none absolute left-1/2 z-10 hidden -translate-x-1/2 whitespace-nowrap rounded-lg bg-slate-900 px-2.5 py-1.5 text-xs text-white shadow-lg group-hover:block group-focus-visible:block"
                style={{ bottom: `calc(${percent}% + 6px)` }}
              >
                <span className="text-slate-300">{column.fullLabel}</span>{' '}
                <span className="font-medium">{column.valueLabel}</span>
              </div>
            </li>
          )
        })}
      </ul>
      <div className="mt-2 flex gap-2" aria-hidden>
        {columns.map((column, i) => (
          <span
            key={column.key}
            className={cn(
              'flex-1 text-center text-xs',
              highlightLast && i === columns.length - 1 ? 'font-medium text-slate-700' : 'text-slate-500',
            )}
          >
            {column.label}
          </span>
        ))}
      </div>
    </figure>
  )
}
