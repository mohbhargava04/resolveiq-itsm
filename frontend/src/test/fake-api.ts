import { vi } from 'vitest'

type Handler = (init: RequestInit & { url: string }) => { status?: number; body?: unknown } | undefined

/**
 * Replaces window.fetch with a tiny fake server: each route is "METHOD /path" mapped to a handler.
 * Every call is recorded so tests can check exactly what the app sent.
 */
export function fakeApi(routes: Record<string, Handler | { status?: number; body?: unknown }>) {
  const calls: { method: string; url: string; headers: Record<string, string>; body?: unknown }[] = []
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const url = typeof input === 'string' ? input : input.toString()
    const method = (init.method ?? 'GET').toUpperCase()
    const headers = Object.fromEntries(Object.entries((init.headers ?? {}) as Record<string, string>))
    const body = typeof init.body === 'string' ? JSON.parse(init.body) : init.body
    calls.push({ method, url, headers, body })
    const route = routes[`${method} ${url.split('?')[0]}`]
    const answer = typeof route === 'function' ? route({ ...init, url }) : route
    if (!answer) return new Response(JSON.stringify({ code: 'RESOURCE_NOT_FOUND' }), { status: 404 })
    return new Response(answer.body === undefined ? '' : JSON.stringify(answer.body), {
      status: answer.status ?? 200, headers: { 'Content-Type': 'application/json' },
    })
  })
  vi.stubGlobal('fetch', fetchMock)
  return { calls, fetchMock }
}

export const demoUser = {
  id: 1, fullName: 'Ava Employee', email: 'ava@resolveiq.test', role: 'EMPLOYEE', active: true,
  profileCompleted: true, passwordEnabled: true, mfaEnabled: false, skills: [],
}

export function session(token: string, user: object = demoUser) {
  return { token, expiresAt: new Date(Date.now() + 15 * 60_000).toISOString(), user }
}
