import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { Category } from '@/lib/types'

export interface CategoryInput {
  name: string
  color: string
}

const categoriesKey = ['categories'] as const

export function useCategories() {
  return useQuery({
    queryKey: categoriesKey,
    queryFn: () => api<Category[]>('/api/categories'),
    staleTime: 5 * 60_000,
  })
}

/** Category changes also affect how expenses render, so both caches are refreshed. */
function useInvalidateCategories() {
  const queryClient = useQueryClient()
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: categoriesKey }),
      queryClient.invalidateQueries({ queryKey: ['expenses'] }),
    ])
}

export function useSaveCategory() {
  const invalidate = useInvalidateCategories()
  return useMutation({
    mutationFn: ({ id, input }: { id?: string; input: CategoryInput }) =>
      id
        ? api<Category>(`/api/categories/${id}`, { method: 'PUT', json: input })
        : api<Category>('/api/categories', { method: 'POST', json: input }),
    onSuccess: invalidate,
  })
}

export function useDeleteCategory() {
  const invalidate = useInvalidateCategories()
  return useMutation({
    mutationFn: (id: string) => api<void>(`/api/categories/${id}`, { method: 'DELETE' }),
    onSuccess: invalidate,
  })
}
