import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ledgerKey, myBalancesKey } from '@/features/ledger/api'
import { api } from '@/lib/api'
import type {
  CreatedInviteLink,
  GroupDetail,
  GroupInput,
  GroupInvite,
  GroupRole,
  GroupSummary,
  InviteLinkPreview,
  MyInvite,
} from '@/lib/types'

const groupKeys = {
  list: ['groups', 'list'] as const,
  detail: (id: string) => ['groups', 'detail', id] as const,
  invites: (id: string) => ['groups', 'invites', id] as const,
}
const myInvitesKey = ['invites', 'mine'] as const

export function useGroups() {
  return useQuery({ queryKey: groupKeys.list, queryFn: () => api<GroupSummary[]>('/api/groups') })
}

export function useGroup(id: string) {
  return useQuery({ queryKey: groupKeys.detail(id), queryFn: () => api<GroupDetail>(`/api/groups/${id}`) })
}

/** Most group mutations return the updated group, so the detail cache is set directly. */
function useStoreGroup() {
  const queryClient = useQueryClient()
  return (group: GroupDetail) => {
    queryClient.setQueryData(groupKeys.detail(group.id), group)
    return Promise.all([
      queryClient.invalidateQueries({ queryKey: groupKeys.list }),
      // Balances carry the group's currency and member list.
      queryClient.invalidateQueries({ queryKey: ledgerKey(group.id) }),
      queryClient.invalidateQueries({ queryKey: myBalancesKey }),
    ])
  }
}

/** Refetch everything about one group (after a change whose response isn't the group itself). */
function useRefreshGroup() {
  const queryClient = useQueryClient()
  return (id: string) =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: groupKeys.detail(id) }),
      queryClient.invalidateQueries({ queryKey: groupKeys.invites(id) }),
      queryClient.invalidateQueries({ queryKey: groupKeys.list }),
      queryClient.invalidateQueries({ queryKey: ledgerKey(id) }),
      queryClient.invalidateQueries({ queryKey: myBalancesKey }),
    ])
}

/**
 * For leaving or deleting: the user can no longer see the group, so drop it from the cache instead
 * of refetching (which would just 404).
 */
function useForgetGroup() {
  const queryClient = useQueryClient()
  return (id: string) => {
    queryClient.removeQueries({ queryKey: groupKeys.detail(id) })
    queryClient.removeQueries({ queryKey: groupKeys.invites(id) })
    queryClient.removeQueries({ queryKey: ledgerKey(id) })
    return Promise.all([
      queryClient.invalidateQueries({ queryKey: groupKeys.list }),
      queryClient.invalidateQueries({ queryKey: myBalancesKey }),
    ])
  }
}

export function useSaveGroup() {
  const storeGroup = useStoreGroup()
  return useMutation({
    mutationFn: ({ id, input }: { id?: string; input: GroupInput }) =>
      id
        ? api<GroupDetail>(`/api/groups/${id}`, { method: 'PUT', json: input })
        : api<GroupDetail>('/api/groups', { method: 'POST', json: input }),
    onSuccess: storeGroup,
  })
}

export function useDeleteGroup() {
  const forgetGroup = useForgetGroup()
  return useMutation({
    mutationFn: (id: string) => api<void>(`/api/groups/${id}`, { method: 'DELETE' }),
    onSuccess: (_, id) => forgetGroup(id),
  })
}

export function useLeaveGroup() {
  const forgetGroup = useForgetGroup()
  return useMutation({
    mutationFn: (id: string) => api<void>(`/api/groups/${id}/leave`, { method: 'POST' }),
    onSuccess: (_, id) => forgetGroup(id),
  })
}

export function useRemoveMember(groupId: string) {
  const refreshGroup = useRefreshGroup()
  return useMutation({
    mutationFn: (userId: string) => api<void>(`/api/groups/${groupId}/members/${userId}`, { method: 'DELETE' }),
    onSettled: () => refreshGroup(groupId),
  })
}

export function useChangeRole(groupId: string) {
  const refreshGroup = useRefreshGroup()
  return useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: GroupRole }) =>
      api(`/api/groups/${groupId}/members/${userId}/role`, { method: 'PUT', json: { role } }),
    // Refetch rather than patch: stepping down changes what the current user is allowed to see.
    onSettled: () => refreshGroup(groupId),
  })
}

// --- Invite link ---

export function useCreateInviteLink(groupId: string) {
  const refreshGroup = useRefreshGroup()
  return useMutation({
    mutationFn: () => api<CreatedInviteLink>(`/api/groups/${groupId}/invite-link`, { method: 'POST' }),
    onSuccess: () => refreshGroup(groupId),
  })
}

export function useDisableInviteLink(groupId: string) {
  const refreshGroup = useRefreshGroup()
  return useMutation({
    mutationFn: () => api<void>(`/api/groups/${groupId}/invite-link`, { method: 'DELETE' }),
    onSuccess: () => refreshGroup(groupId),
  })
}

/** A POST so the token travels in the body, not the URL, but it's a read: safe to cache and retry. */
export function useInviteLinkPreview(token: string) {
  return useQuery({
    queryKey: ['invite-link', token],
    queryFn: () => api<InviteLinkPreview>('/api/invite-links/preview', { method: 'POST', json: { token } }),
  })
}

export function useJoinByLink() {
  const storeGroup = useStoreGroup()
  return useMutation({
    mutationFn: (token: string) => api<GroupDetail>('/api/invite-links/accept', { method: 'POST', json: { token } }),
    onSuccess: storeGroup,
  })
}

// --- Email invites (owner side) ---

export function useGroupInvites(groupId: string) {
  return useQuery({
    queryKey: groupKeys.invites(groupId),
    queryFn: () => api<GroupInvite[]>(`/api/groups/${groupId}/invites`),
  })
}

export function useInviteByEmail(groupId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (email: string) =>
      api<GroupInvite>(`/api/groups/${groupId}/invites`, { method: 'POST', json: { email } }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: groupKeys.invites(groupId) }),
  })
}

export function useCancelInvite(groupId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (inviteId: string) => api<void>(`/api/groups/${groupId}/invites/${inviteId}`, { method: 'DELETE' }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: groupKeys.invites(groupId) }),
  })
}

// --- Email invites (recipient side) ---

export function useMyInvites() {
  return useQuery({ queryKey: myInvitesKey, queryFn: () => api<MyInvite[]>('/api/invites') })
}

export function useRespondToInvite() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, accept }: { id: string; accept: boolean }) =>
      api<GroupDetail | undefined>(`/api/invites/${id}/${accept ? 'accept' : 'decline'}`, { method: 'POST' }),
    onSettled: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: myInvitesKey }),
        queryClient.invalidateQueries({ queryKey: groupKeys.list }),
        queryClient.invalidateQueries({ queryKey: myBalancesKey }),
      ]),
  })
}
