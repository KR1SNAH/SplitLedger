import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type {
  ExpenseShare,
  GroupBalances,
  GroupExpense,
  GroupExpenseInput,
  MyBalances,
  Page,
  Settlement,
  SettlementInput,
  SplitPreviewInput,
} from '@/lib/types'

/** Everything money-related in one group lives under this key, so any change refreshes it all. */
export const ledgerKey = (groupId: string) => ['groups', 'ledger', groupId] as const

const ledgerKeys = {
  expenses: (groupId: string, page: number) => [...ledgerKey(groupId), 'expenses', page] as const,
  balances: (groupId: string) => [...ledgerKey(groupId), 'balances'] as const,
  settlements: (groupId: string) => [...ledgerKey(groupId), 'settlements'] as const,
  preview: (groupId: string, input: SplitPreviewInput | null) => [...ledgerKey(groupId), 'preview', input] as const,
}

/** Your balance in every group. Refreshed by any ledger change and by joining, leaving or deleting a group. */
export const myBalancesKey = ['groups', 'my-balances'] as const

export const EXPENSES_PAGE_SIZE = 10

export function useMyBalances() {
  return useQuery({ queryKey: myBalancesKey, queryFn: () => api<MyBalances>('/api/balances') })
}

export function useGroupExpenses(groupId: string, page: number) {
  return useQuery({
    queryKey: ledgerKeys.expenses(groupId, page),
    queryFn: () =>
      api<Page<GroupExpense>>(`/api/groups/${groupId}/expenses?page=${page}&size=${EXPENSES_PAGE_SIZE}`),
    placeholderData: keepPreviousData,
  })
}

export function useGroupBalances(groupId: string) {
  return useQuery({
    queryKey: ledgerKeys.balances(groupId),
    queryFn: () => api<GroupBalances>(`/api/groups/${groupId}/balances`),
  })
}

export function useSettlements(groupId: string) {
  return useQuery({
    queryKey: ledgerKeys.settlements(groupId),
    queryFn: () => api<Settlement[]>(`/api/groups/${groupId}/settlements`),
  })
}

/**
 * The shares the server would save for this split. The browser never does the money math itself:
 * leftover cents and validation follow exactly the same rules as saving. Pass null to skip.
 */
export function useSplitPreview(groupId: string, input: SplitPreviewInput | null) {
  return useQuery({
    queryKey: ledgerKeys.preview(groupId, input),
    queryFn: () =>
      api<ExpenseShare[]>(`/api/groups/${groupId}/expenses/preview`, { method: 'POST', json: input }),
    enabled: input !== null,
    retry: false,
    staleTime: Infinity,
  })
}

function useRefreshLedger(groupId: string) {
  const queryClient = useQueryClient()
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ledgerKey(groupId) }),
      queryClient.invalidateQueries({ queryKey: myBalancesKey }),
    ])
}

export function useSaveGroupExpense(groupId: string) {
  const refreshLedger = useRefreshLedger(groupId)
  return useMutation({
    mutationFn: ({ id, input }: { id?: string; input: GroupExpenseInput }) =>
      id
        ? api<GroupExpense>(`/api/groups/${groupId}/expenses/${id}`, { method: 'PUT', json: input })
        : api<GroupExpense>(`/api/groups/${groupId}/expenses`, { method: 'POST', json: input }),
    // Refetch on failure too: a 409 means our copy is stale.
    onSettled: refreshLedger,
  })
}

export function useDeleteGroupExpense(groupId: string) {
  const refreshLedger = useRefreshLedger(groupId)
  return useMutation({
    mutationFn: (id: string) => api<void>(`/api/groups/${groupId}/expenses/${id}`, { method: 'DELETE' }),
    onSettled: refreshLedger,
  })
}

export function useRecordSettlement(groupId: string) {
  const refreshLedger = useRefreshLedger(groupId)
  return useMutation({
    mutationFn: (input: SettlementInput) =>
      api<Settlement>(`/api/groups/${groupId}/settlements`, { method: 'POST', json: input }),
    onSuccess: refreshLedger,
  })
}

export function useDeleteSettlement(groupId: string) {
  const refreshLedger = useRefreshLedger(groupId)
  return useMutation({
    mutationFn: (id: string) => api<void>(`/api/groups/${groupId}/settlements/${id}`, { method: 'DELETE' }),
    onSettled: refreshLedger,
  })
}
