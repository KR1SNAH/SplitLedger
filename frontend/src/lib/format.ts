const moneyFormatters = new Map<string, Intl.NumberFormat>()

export function formatMoney(amount: number, currency: string) {
  let formatter = moneyFormatters.get(currency)
  if (!formatter) {
    formatter = new Intl.NumberFormat(undefined, { style: 'currency', currency })
    moneyFormatters.set(currency, formatter)
  }
  return formatter.format(amount)
}

/** Formats an ISO date (YYYY-MM-DD) without the UTC shift `new Date(iso)` would apply. */
export function formatDate(isoDate: string) {
  const [year, month, day] = isoDate.split('-').map(Number)
  return new Date(year, month - 1, day).toLocaleDateString(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  })
}

/** Local calendar date as YYYY-MM-DD. */
export function toIsoDate(date: Date) {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

export function currentMonthRange() {
  return monthRange(currentMonth())
}

/** The local calendar month as YYYY-MM, the format the API uses for months. */
export function currentMonth() {
  return toIsoDate(new Date()).slice(0, 7)
}

function parseMonth(month: string) {
  const [year, monthNumber] = month.split('-').map(Number)
  return { year, index: monthNumber - 1 }
}

/** Moves a YYYY-MM month by whole months, e.g. shiftMonth('2026-01', -1) is '2025-12'. */
export function shiftMonth(month: string, delta: number) {
  const { year, index } = parseMonth(month)
  return toIsoDate(new Date(year, index + delta, 1)).slice(0, 7)
}

/** First and last day of a YYYY-MM month, as ISO dates. */
export function monthRange(month: string) {
  const { year, index } = parseMonth(month)
  return { from: toIsoDate(new Date(year, index, 1)), to: toIsoDate(new Date(year, index + 1, 0)) }
}

/** 'long': "September 2026". 'short': "Sep". 'name': "September". */
export function formatMonth(month: string, style: 'long' | 'short' | 'name' = 'long') {
  const { year, index } = parseMonth(month)
  const options: Intl.DateTimeFormatOptions =
    style === 'long' ? { month: 'long', year: 'numeric' } : { month: style === 'short' ? 'short' : 'long' }
  return new Date(year, index, 1).toLocaleDateString(undefined, options)
}

export const CURRENCIES = [
  { code: 'USD', label: 'US Dollar' },
  { code: 'EUR', label: 'Euro' },
  { code: 'GBP', label: 'British Pound' },
  { code: 'INR', label: 'Indian Rupee' },
  { code: 'CAD', label: 'Canadian Dollar' },
  { code: 'AUD', label: 'Australian Dollar' },
  { code: 'SGD', label: 'Singapore Dollar' },
  { code: 'AED', label: 'UAE Dirham' },
  { code: 'JPY', label: 'Japanese Yen' },
  { code: 'CHF', label: 'Swiss Franc' },
] as const

/** Formats a timestamp (ISO instant) as a local calendar date. */
export function formatInstant(instant: string) {
  return new Date(instant).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })
}
