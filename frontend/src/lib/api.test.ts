import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError, qs, refreshSession, session, setSessionHandlers } from './api'
import { fakeApi, session as sessionBody } from '@/test/fake-api'

describe('API client and session handling', () => {
  beforeEach(async () => {
    // a finished refresh is shared until the next tick; let any from the previous test expire
    await new Promise((r) => setTimeout(r, 0))
    session.clear()
    setSessionHandlers({ ended: () => {}, refreshed: () => {} })
  })

  it('FE-01 sends the access token as a Bearer header and never stores it in localStorage', async () => {
    const { calls } = fakeApi({ 'GET /api/incidents': { body: [] } })
    session.set(sessionBody('token-A'))
    await api.get('/incidents')
    expect(calls[0].headers.Authorization).toBe('Bearer token-A')
    expect(JSON.stringify({ ...localStorage })).not.toContain('token-A')
  })

  it('FE-02 a 401 triggers one silent refresh and the request is retried with the new token', async () => {
    const { calls } = fakeApi({
      'GET /api/incidents': (init) => {
        const auth = (init.headers as Record<string, string>).Authorization
        return auth === 'Bearer fresh' ? { body: [{ number: 'INC-1' }] } : { status: 401, body: { code: 'UNAUTHENTICATED' } }
      },
      'POST /api/auth/refresh': { body: sessionBody('fresh') },
    })
    session.set(sessionBody('stale'))
    const result = await api.get<{ number: string }[]>('/incidents')
    expect(result[0].number).toBe('INC-1')
    expect(calls.map((c) => `${c.method} ${c.url}`)).toEqual([
      'GET /api/incidents', 'POST /api/auth/refresh', 'GET /api/incidents',
    ])
  })

  it('FE-03 concurrent refreshes share a single request (refresh tokens work only once)', async () => {
    const { calls } = fakeApi({ 'POST /api/auth/refresh': { body: sessionBody('fresh') } })
    const [a, b, c] = await Promise.all([refreshSession(), refreshSession(), refreshSession()])
    expect(calls.filter((x) => x.url === '/api/auth/refresh')).toHaveLength(1)
    expect(a?.token).toBe('fresh')
    expect(b).toBe(a)
    expect(c).toBe(a)
  })

  it('FE-04 cookie endpoints carry the anti-CSRF header; ordinary API calls do not need it', async () => {
    const { calls } = fakeApi({
      'POST /api/auth/refresh': { body: sessionBody('t') },
      'GET /api/kb': { body: [] },
    })
    await refreshSession()
    await api.get('/kb')
    expect(calls[0].headers['X-Requested-With']).toBe('ResolveIQ')
    expect(calls[1].headers['X-Requested-With']).toBeUndefined()
  })

  it('FE-05 when the refresh fails the session ends and the app is told', async () => {
    const ended = vi.fn()
    setSessionHandlers({ ended, refreshed: () => {} })
    fakeApi({
      'GET /api/incidents': { status: 401, body: { code: 'UNAUTHENTICATED' } },
      'POST /api/auth/refresh': { status: 401, body: { code: 'SESSION_EXPIRED' } },
    })
    session.set(sessionBody('old'))
    await expect(api.get('/incidents')).rejects.toBeInstanceOf(ApiError)
    expect(ended).toHaveBeenCalledOnce()
    expect(session.token).toBeNull()
  })

  it('FE-06 problem responses become ApiErrors with the stable code and field errors', async () => {
    fakeApi({
      'POST /api/incidents': {
        status: 400,
        body: { code: 'VALIDATION_FAILED', detail: 'Check the form', fieldErrors: { title: 'must not be blank' } },
      },
    })
    const error = await api.post('/incidents', {}).catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 400, code: 'VALIDATION_FAILED', message: 'Check the form' })
    expect((error as ApiError).fieldErrors?.title).toBe('must not be blank')
  })

  it('FE-07 query strings skip empty values and repeat array keys', () => {
    expect(qs({ status: ['NEW', 'ASSIGNED'], q: '', page: 0, mine: false, service: undefined })).toBe('?status=NEW&status=ASSIGNED&page=0')
    expect(qs({})).toBe('')
  })
})
