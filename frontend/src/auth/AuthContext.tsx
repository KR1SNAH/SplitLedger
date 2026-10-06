import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, refreshSession, setAccessToken, setSessionEndedHandler } from '@/lib/api'
import type { AuthResponse, User } from '@/lib/types'
import { AuthContext, type AuthContextValue } from './context'

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [user, setUser] = useState<User | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  const endSession = useCallback(() => {
    setAccessToken(null)
    setUser(null)
    queryClient.clear()
  }, [queryClient])

  useEffect(() => {
    setSessionEndedHandler(endSession)
    refreshSession()
      .then((session) => setUser(session?.user ?? null))
      .finally(() => setIsLoading(false))
    return () => setSessionEndedHandler(null)
  }, [endSession])

  const startSession = useCallback((session: AuthResponse) => {
    setAccessToken(session.accessToken)
    setUser(session.user)
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isLoading,
      login: async (email, password) => {
        startSession(await api<AuthResponse>('/api/auth/login', { method: 'POST', json: { email, password } }))
      },
      register: async (input) => {
        startSession(await api<AuthResponse>('/api/auth/register', { method: 'POST', json: input }))
      },
      logout: async () => {
        try {
          await api<void>('/api/auth/logout', { method: 'POST' })
        } finally {
          endSession()
        }
      },
      updateUser: setUser,
    }),
    [user, isLoading, startSession, endSession],
  )

  return <AuthContext value={value}>{children}</AuthContext>
}
