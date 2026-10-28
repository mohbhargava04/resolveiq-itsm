// Thin fetch wrapper: attaches the JWT, parses RFC 7807 problem responses into ApiError,
// and signs the user out when the token is rejected.

const TOKEN_KEY = 'riq-token'

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

export const tokenStore = {
  get: (): string | null => {
    try { return localStorage.getItem(TOKEN_KEY) } catch { return null }
  },
  set: (token: string) => {
    try { localStorage.setItem(TOKEN_KEY, token) } catch { /* private mode */ }
  },
  clear: () => {
    try { localStorage.removeItem(TOKEN_KEY) } catch { /* private mode */ }
  },
}

let onUnauthorized: (() => void) | null = null
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler
}

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

async function request<T>(method: Method, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  const token = tokenStore.get()
  if (token) headers.Authorization = `Bearer ${token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  let res: Response
  try {
    res = await fetch(`/api${path}`, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) })
  } catch {
    throw new ApiError(0, null, 'Cannot reach the ResolveIQ server. Check that the backend is running.')
  }

  const text = await res.text()
  let data: unknown = null
  if (text) {
    try { data = JSON.parse(text) } catch { data = text }
  }
  if (!res.ok) {
    const problem = (data && typeof data === 'object' ? data : null) as Record<string, unknown> | null
    if (res.status === 401 && !path.startsWith('/auth/')) onUnauthorized?.()
    throw new ApiError(res.status, problem, `Request failed (${res.status})`)
  }
  return data as T
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body ?? {}),
  put: <T>(path: string, body: unknown) => request<T>('PUT', path, body),
  patch: <T>(path: string, body: unknown) => request<T>('PATCH', path, body),
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

export async function downloadCsv(path: string, filename: string) {
  const res = await fetch(`/api${path}`, { headers: { Authorization: `Bearer ${tokenStore.get() ?? ''}` } })
  if (!res.ok) throw new ApiError(res.status, null, 'Export failed')
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}
