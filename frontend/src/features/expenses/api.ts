import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { Expense, ExpenseFilters, ExpenseInput, ExpenseSummary, MonthTotal, Page } from '@/lib/types'

export interface ExpenseListParams extends ExpenseFilters {
  page?: number
  size?: number
  sort?: string
}

function toQueryString(params: object) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.set(key, String(value))
  })
  const query = search.toString()
  return query ? `?${query}` : ''
}

export function useExpenses(params: ExpenseListParams) {
  return useQuery({
    queryKey: ['expenses', 'list', params],
    queryFn: () => api<Page<Expense>>(`/api/expenses${toQueryString(params)}`),
    placeholderData: keepPreviousData,
  })
}

export function useExpenseSummary(filters: ExpenseFilters) {
  return useQuery({
    queryKey: ['expenses', 'summary', filters],
    queryFn: () => api<ExpenseSummary>(`/api/expenses/summary${toQueryString(filters)}`),
    placeholderData: keepPreviousData,
  })
}

/** Spending per month for the `months` months ending with `to` (YYYY-MM), oldest first. */
export function useMonthlyTotals(to: string, months: number) {
  return useQuery({
    queryKey: ['expenses', 'monthly', { to, months }],
    queryFn: () => api<MonthTotal[]>(`/api/expenses/monthly${toQueryString({ to, months })}`),
  })
}

export function useSaveExpense() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id?: string; input: ExpenseInput }) =>
      id
        ? api<Expense>(`/api/expenses/${id}`, { method: 'PUT', json: input })
        : api<Expense>('/api/expenses', { method: 'POST', json: input }),
    // Refetch on failure too: a 409 means our copy is stale.
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['expenses'] }),
  })
}

export function useDeleteExpense() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api<void>(`/api/expenses/${id}`, { method: 'DELETE' }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['expenses'] }),
  })
}
