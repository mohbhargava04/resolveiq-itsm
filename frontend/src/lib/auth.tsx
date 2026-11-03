import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { api, CSRF_HEADER, refreshSession, session, setSessionHandlers } from './api'
import type { AuthResponse, Role, User } from './types'

/** Result of the first sign-in step: signed in, or a two-factor code is needed. */
export type SignInResult = { kind: 'signed-in'; user: User } | { kind: 'mfa'; challenge: string }

interface AuthState {
  user: User | null
  loading: boolean
  /** An administrator must set up 2FA before using the app (policy set in Automation settings). */
  mfaSetupRequired: boolean
  login: (email: string, password: string) => Promise<SignInResult>
  verifyMfa: (code: string, challenge?: string) => Promise<User>
  register: (data: { fullName: string; email: string; password: string; phone?: string; department?: string }) => Promise<User>
  /** Accepts a session returned by an endpoint (e.g. after changing the password). */
  accept: (res: AuthResponse) => User
  setUser: (u: User) => void
  logout: () => Promise<void>
  refresh: () => Promise<void>
  /** Role checks follow the backend hierarchy: MANAGER > ADMIN > TEAM_LEAD > TECHNICIAN. */
  is: (minimum: Role) => boolean
}

const AuthContext = createContext<AuthState | null>(null)

const RANK: Record<Role, number> = { EMPLOYEE: 0, TECHNICIAN: 1, TEAM_LEAD: 2, ADMIN: 3, MANAGER: 4 }

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)
  const [mfaSetupRequired, setMfaSetupRequired] = useState(false)
  const queryClient = useQueryClient()

  const endLocally = useCallback(() => {
    session.clear()
    setUser(null)
    setMfaSetupRequired(false)
    queryClient.clear()
  }, [queryClient])

  const accept = useCallback((res: AuthResponse) => {
    session.set(res)
    setUser(res.user)
    setMfaSetupRequired(!!res.mfaSetupRequired)
    return res.user
  }, [])

  // On load, the HttpOnly refresh cookie (if any) restores the session without a password.
  useEffect(() => {
    setSessionHandlers({
      ended: endLocally,
      refreshed: (res) => { setUser(res.user); setMfaSetupRequired(!!res.mfaSetupRequired) },
    })
    // only try when the server's "signed in" hint cookie exists (avoids a pointless 401 on the sign-in page)
    const hint = typeof document !== 'undefined' && document.cookie.split('; ').some((c) => c.startsWith('riq_signed_in='))
    if (!hint) { setLoading(false); return }
    refreshSession().then((res) => { if (res) accept(res) }).finally(() => setLoading(false))
  }, [endLocally, accept])

  const refresh = useCallback(async () => {
    if (!session.token) return
    try {
      setUser(await api.get<User>('/auth/me'))
    } catch {
      endLocally()
    }
  }, [endLocally])

  const logout = useCallback(async () => {
    try {
      await fetch('/api/auth/logout', { method: 'POST', headers: CSRF_HEADER, credentials: 'same-origin' })
    } finally {
      endLocally()
    }
  }, [endLocally])

  const value = useMemo<AuthState>(() => ({
    user,
    loading,
    mfaSetupRequired,
    login: async (email, password) => {
      const res = await api.post<AuthResponse>('/auth/login', { email, password })
      if (res.mfaRequired && res.challenge) return { kind: 'mfa', challenge: res.challenge }
      queryClient.clear()
      return { kind: 'signed-in', user: accept(res) }
    },
    verifyMfa: async (code, challenge) => {
      const res = await api.post<AuthResponse>('/auth/mfa/verify', { code, challenge })
      queryClient.clear()
      return accept(res)
    },
    register: async (data) => {
      const res = await api.post<AuthResponse>('/auth/register', data)
      queryClient.clear()
      return accept(res)
    },
    accept,
    setUser,
    logout,
    refresh,
    is: (minimum) => !!user && user.role !== 'EMPLOYEE' && RANK[user.role] >= RANK[minimum],
  }), [user, loading, mfaSetupRequired, accept, logout, refresh, queryClient])

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
