import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { BudgetStatus } from '@/lib/types'

/**
 * Budget status is derived from expenses, so it lives under the 'expenses' key: every expense or
 * category change (which already invalidates ['expenses']) refreshes the budgets too.
 */
const budgetsKey = ['expenses', 'budgets'] as const

export interface BudgetInput {
  categoryId: string | null
  amount: number
}

export function useBudgets(month: string) {
  return useQuery({
    queryKey: [...budgetsKey, month],
    queryFn: () => api<BudgetStatus[]>(`/api/budgets?month=${month}`),
    placeholderData: keepPreviousData,
  })
}

function useInvalidateBudgets() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: budgetsKey })
}

export function useCreateBudget() {
  const invalidate = useInvalidateBudgets()
  return useMutation({
    mutationFn: (input: BudgetInput) => api('/api/budgets', { method: 'POST', json: input }),
    onSuccess: invalidate,
  })
}

export function useUpdateBudget() {
  const invalidate = useInvalidateBudgets()
  return useMutation({
    mutationFn: ({ id, amount }: { id: string; amount: number }) =>
      api(`/api/budgets/${id}`, { method: 'PUT', json: { amount } }),
    onSettled: invalidate,
  })
}

export function useDeleteBudget() {
  const invalidate = useInvalidateBudgets()
  return useMutation({
    mutationFn: (id: string) => api<void>(`/api/budgets/${id}`, { method: 'DELETE' }),
    onSettled: invalidate,
  })
}
