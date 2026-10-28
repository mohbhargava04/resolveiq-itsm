import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { api, setUnauthorizedHandler, tokenStore } from './api'
import type { AuthResponse, Role, User } from './types'

interface AuthState {
  user: User | null
  loading: boolean
  login: (email: string, password: string) => Promise<User>
  register: (data: { fullName: string; email: string; password: string; phone?: string; department?: string }) => Promise<User>
  logout: () => void
  refresh: () => Promise<void>
  /** Role checks follow the backend hierarchy: MANAGER > ADMIN > TEAM_LEAD > TECHNICIAN. */
  is: (minimum: Role) => boolean
}

const AuthContext = createContext<AuthState | null>(null)

const RANK: Record<Role, number> = { EMPLOYEE: 0, TECHNICIAN: 1, TEAM_LEAD: 2, ADMIN: 3, MANAGER: 4 }

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)
  const queryClient = useQueryClient()

  const logout = useCallback(() => {
    tokenStore.clear()
    setUser(null)
    queryClient.clear()
  }, [queryClient])

  const refresh = useCallback(async () => {
    if (!tokenStore.get()) {
      setUser(null)
      return
    }
    try {
      setUser(await api.get<User>('/auth/me'))
    } catch {
      tokenStore.clear()
      setUser(null)
    }
  }, [])

  useEffect(() => {
    setUnauthorizedHandler(logout)
    refresh().finally(() => setLoading(false))
  }, [logout, refresh])

  const accept = useCallback((res: AuthResponse) => {
    tokenStore.set(res.token)
    queryClient.clear()
    setUser(res.user)
    return res.user
  }, [queryClient])

  const value = useMemo<AuthState>(() => ({
    user,
    loading,
    login: async (email, password) => accept(await api.post<AuthResponse>('/auth/login', { email, password })),
    register: async (data) => accept(await api.post<AuthResponse>('/auth/register', data)),
    logout,
    refresh,
    is: (minimum) => !!user && user.role !== 'EMPLOYEE' && RANK[user.role] >= RANK[minimum],
  }), [user, loading, accept, logout, refresh])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}

export function useUser(): User {
  const { user } = useAuth()
  if (!user) throw new Error('No signed-in user')
  return user
}
