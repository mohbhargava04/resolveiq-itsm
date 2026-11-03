// Fetch wrapper for the ResolveIQ API.
//
// Security model (see docs/SECURITY.md):
//  - The access token lives only in this module's memory: never in localStorage, so an injected
//    script cannot read it from storage, and it disappears when the tab closes.
//  - The refresh token is an HttpOnly cookie the browser sends only to /api/auth; JavaScript never sees it.
//  - When the access token expires (or the API answers 401) we refresh once, transparently, and retry.
//  - Cookie endpoints get the X-Requested-With header, which other websites cannot add (CSRF defence).

import type { AuthResponse } from './types'

export class ApiError extends Error {
  status: number
  code?: string
  details: Record<string, unknown>
  fieldErrors?: Record<string, string>

  constructor(status: number, body: Record<string, unknown> | null, fallback: string) {
    super((body?.detail as string) || fallback)
    this.status = status
    this.code = body?.code as string | undefined
    this.details = body ?? {}
    this.fieldErrors = body?.fieldErrors as Record<string, string> | undefined
  }
}

export const CSRF_HEADER = { 'X-Requested-With': 'ResolveIQ' }

let accessToken: string | null = null
let expiresAt = 0
let refreshTimer: ReturnType<typeof setTimeout> | undefined
let inFlightRefresh: Promise<AuthResponse | null> | null = null
let onSessionEnded: (() => void) | null = null
let onSessionRefreshed: ((res: AuthResponse) => void) | null = null

export const session = {
  get token() { return accessToken },
  /** Stores a new access token and schedules a silent refresh one minute before it expires. */
  set(res: { token: string; expiresAt: string }) {
    accessToken = res.token
    expiresAt = Date.parse(res.expiresAt)
    clearTimeout(refreshTimer)
    const inMs = Math.max(5_000, expiresAt - Date.now() - 60_000)
    refreshTimer = setTimeout(() => { void refreshSession() }, inMs)
  },
  clear() {
    accessToken = null
    expiresAt = 0
    clearTimeout(refreshTimer)
  },
}

export function setSessionHandlers(handlers: { ended: () => void; refreshed: (res: AuthResponse) => void }) {
  onSessionEnded = handlers.ended
  onSessionRefreshed = handlers.refreshed
}

/**
 * Exchanges the refresh cookie for a new access token. Concurrent callers share one request, so a
 * burst of 401s never sends several refreshes (each refresh token works only once).
 */
export function refreshSession(): Promise<AuthResponse | null> {
  if (!inFlightRefresh) {
    inFlightRefresh = (async () => {
      try {
        const res = await fetch('/api/auth/refresh', { method: 'POST', headers: { Accept: 'application/json', ...CSRF_HEADER }, credentials: 'same-origin' })
        if (!res.ok) {
          session.clear()
          return null
        }
        const body = (await res.json()) as AuthResponse
        session.set(body)
        onSessionRefreshed?.(body)
        return body
      } catch {
        return null
      } finally {
        setTimeout(() => { inFlightRefresh = null }, 0)
      }
    })()
  }
  return inFlightRefresh
}

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

async function send(method: Method, path: string, body: unknown, form: FormData | undefined): Promise<Response> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`
  if (body !== undefined && !form) headers['Content-Type'] = 'application/json'
  if (path.startsWith('/auth/')) Object.assign(headers, CSRF_HEADER)
  try {
    return await fetch(`/api${path}`, {
      method, headers, credentials: 'same-origin',
      body: form ?? (body === undefined ? undefined : JSON.stringify(body)),
    })
  } catch {
    throw new ApiError(0, null, 'Cannot reach the ResolveIQ server. Check that the backend is running.')
  }
}

async function request<T>(method: Method, path: string, body?: unknown, form?: FormData): Promise<T> {
  // refresh slightly early instead of sending a token that is about to expire
  if (accessToken && expiresAt && expiresAt - Date.now() < 10_000 && !path.startsWith('/auth/')) {
    await refreshSession()
  }
  let res = await send(method, path, body, form)
  if (res.status === 401 && !path.startsWith('/auth/') && accessToken !== null) {
    const refreshed = await refreshSession()
    if (refreshed) {
      res = await send(method, path, body, form)
    }
    if (res.status === 401) onSessionEnded?.()
  }

  const text = await res.text()
  let data: unknown = null
  if (text) {
    try { data = JSON.parse(text) } catch { data = text }
  }
  if (!res.ok) {
    const problem = (data && typeof data === 'object' ? data : null) as Record<string, unknown> | null
    throw new ApiError(res.status, problem, `Request failed (${res.status})`)
  }
  return data as T
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body ?? {}),
  put: <T>(path: string, body: unknown) => request<T>('PUT', path, body),
  patch: <T>(path: string, body: unknown) => request<T>('PATCH', path, body),
  delete: <T>(path: string) => request<T>('DELETE', path),
  upload: <T>(path: string, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return request<T>('POST', path, undefined, form)
  },
}

/** Builds a query string, skipping empty values and repeating keys for arrays. */
export function qs(params: Record<string, unknown>): string {
  const sp = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === '' || v === false) continue
    if (Array.isArray(v)) v.forEach((x) => sp.append(k, String(x)))
    else sp.append(k, String(v))
  }
  const s = sp.toString()
  return s ? `?${s}` : ''
}

/** Fetches a protected file (the browser cannot add the Authorization header to <img> or <a>). */
export async function fetchBlob(path: string): Promise<Blob> {
  let res = await fetch(`/api${path}`, { headers: { Authorization: `Bearer ${accessToken ?? ''}` }, credentials: 'same-origin' })
  if (res.status === 401 && await refreshSession()) {
    res = await fetch(`/api${path}`, { headers: { Authorization: `Bearer ${accessToken ?? ''}` }, credentials: 'same-origin' })
  }
  if (!res.ok) throw new ApiError(res.status, null, 'Download failed')
  return res.blob()
}

export async function downloadFile(path: string, filename: string) {
  const blob = await fetchBlob(path)
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

/** Kept for existing callers (CSV export on the reports page). */
export const downloadCsv = downloadFile
