import { createContext } from 'react'
import type { User } from '@/lib/types'

export interface RegisterInput {
  email: string
  password: string
  displayName: string
}

export interface AuthContextValue {
  user: User | null
  /** True until the initial session restore (via the refresh cookie) has finished. */
  isLoading: boolean
  login: (email: string, password: string) => Promise<void>
  register: (input: RegisterInput) => Promise<void>
  logout: () => Promise<void>
  /** Replaces the cached user after a profile change. */
  updateUser: (user: User) => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)
