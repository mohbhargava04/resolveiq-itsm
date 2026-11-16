import { describe, expect, it } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { ThemeProvider } from '@/lib/theme'
import { AuthProvider, useAuth } from '@/lib/auth'
import { session } from '@/lib/api'
import { applyDisplay, DEFAULT_DISPLAY, normaliseDisplay, readStoredDisplay, resolveTheme } from '@/lib/display'
import { demoUser, fakeApi, session as sessionBody } from '@/test/fake-api'
import { DisplayAccountSync, DisplaySettings } from './DisplaySettings'

function SignInButton() {
  const { login, user } = useAuth()
  return user ? <p>Signed in</p> : <button onClick={() => login('ava@resolveiq.test', 'x')}>Sign in</button>
}

function renderSettings() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <ThemeProvider>
      <QueryClientProvider client={client}>
        <MemoryRouter>
          <AuthProvider>
            <DisplayAccountSync />
            <SignInButton />
            <DisplaySettings />
          </AuthProvider>
        </MemoryRouter>
      </QueryClientProvider>
    </ThemeProvider>,
  )
}

const root = () => document.documentElement

describe('Display & accessibility settings', () => {
  it('FE-17 resolves themes and repairs invalid stored values', () => {
    expect(resolveTheme('SYSTEM', true)).toBe('dark')
    expect(resolveTheme('SYSTEM', false)).toBe('light')
    expect(resolveTheme('HIGH_CONTRAST', true)).toBe('contrast')
    expect(normaliseDisplay({ theme: 'NEON', textSize: 'HUGE', reduceMotion: 'yes' })).toEqual(DEFAULT_DISPLAY)
    localStorage.setItem('riq-theme', 'dark') // what the app stored before these settings existed
    expect(readStoredDisplay().theme).toBe('DARK')
  })

  it('FE-18 applying settings sets the theme, text size and reading aids on <html>', () => {
    const el = document.createElement('html')
    applyDisplay({ theme: 'DIM', textSize: 'EXTRA_LARGE', reduceMotion: true, easierReading: true, underlineLinks: true }, false, el)
    expect(el.dataset).toMatchObject({ theme: 'dim', textSize: 'xl', motion: 'reduce', reading: 'easy', links: 'underline' })
    expect(el.classList.contains('dark')).toBe(true)
    applyDisplay(DEFAULT_DISPLAY, false, el)
    expect(el.dataset.theme).toBe('light')
    expect(el.dataset.motion).toBeUndefined()
    expect(el.classList.contains('dark')).toBe(false)
  })

  it('FE-19 choosing options changes the page at once and is remembered on this device', async () => {
    session.clear()
    fakeApi({})
    const user = userEvent.setup()
    renderSettings()
    await user.click(screen.getByRole('radio', { name: /High contrast/ }))
    await user.click(screen.getByRole('radio', { name: 'Extra large' }))
    await user.click(screen.getByRole('switch', { name: 'Underline links' }))
    expect(root().dataset.theme).toBe('contrast')
    expect(root().dataset.textSize).toBe('xl')
    expect(root().dataset.links).toBe('underline')
    expect(screen.getByRole('radio', { name: /High contrast/ })).toHaveAttribute('aria-checked', 'true')
    expect(JSON.parse(localStorage.getItem('riq-display')!)).toMatchObject({ theme: 'HIGH_CONTRAST', textSize: 'EXTRA_LARGE', underlineLinks: true })
    await user.click(screen.getByRole('button', { name: 'Reset to defaults' }))
    expect(root().dataset.theme).toBe('light')
  })

  it('FE-20 signing in loads the settings saved on the account, and changes are saved back to it', async () => {
    session.clear()
    const saved = { theme: 'WARM', textSize: 'LARGE', reduceMotion: false, easierReading: true, underlineLinks: false }
    const { calls } = fakeApi({
      'POST /api/auth/login': { body: sessionBody('tok', { ...demoUser, display: saved }) },
      'PUT /api/auth/preferences': (init) => ({ body: { ...demoUser, display: JSON.parse(init.body as string) } }),
    })
    const user = userEvent.setup()
    renderSettings()
    await user.click(screen.getByRole('button', { name: 'Sign in' }))
    await waitFor(() => expect(root().dataset.theme).toBe('warm'))
    expect(root().dataset.textSize).toBe('large')
    expect(root().dataset.reading).toBe('easy')
    expect(calls.some((c) => c.method === 'PUT')).toBe(false) // loading must not write back

    await user.click(screen.getByRole('switch', { name: 'Reduce motion' }))
    await waitFor(() => expect(calls.find((c) => c.method === 'PUT' && c.url === '/api/auth/preferences')?.body)
      .toMatchObject({ theme: 'WARM', reduceMotion: true }), { timeout: 2000 })
  })
})
