import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { AdminStats, Page, User } from '@/lib/types'

const adminKey = ['admin'] as const

export const USERS_PAGE_SIZE = 20

export function useAdminStats() {
  return useQuery({ queryKey: [...adminKey, 'stats'], queryFn: () => api<AdminStats>('/api/admin/stats') })
}

export function useAdminUsers(page: number) {
  return useQuery({
    queryKey: [...adminKey, 'users', page],
    queryFn: () => api<Page<User>>(`/api/admin/users?page=${page}&size=${USERS_PAGE_SIZE}`),
    placeholderData: keepPreviousData,
  })
}

/** Disabling signs the user out everywhere, immediately. */
export function useSetUserEnabled() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, enabled }: { id: string; enabled: boolean }) =>
      api<User>(`/api/admin/users/${id}/${enabled ? 'enable' : 'disable'}`, { method: 'POST' }),
    // Both the user list and the stats (disabled count) change.
    onSettled: () => queryClient.invalidateQueries({ queryKey: adminKey }),
  })
}
