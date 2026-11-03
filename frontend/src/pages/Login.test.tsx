import { describe, expect, it } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { ThemeProvider } from '@/lib/theme'
import { AuthProvider } from '@/lib/auth'
import { session } from '@/lib/api'
import { fakeApi, session as sessionBody } from '@/test/fake-api'
import Login from './Login'

const OPTIONS = {
  providers: [], selfRegistration: true, allowedDomains: ['resolveiq.test'], demoMode: true, demoPassword: 'Password@123',
  demoAccounts: [
    { role: 'MANAGER', label: 'IT manager', email: 'manager@resolveiq.test', lands: 'Everything, including roles' },
    { role: 'EMPLOYEE', label: 'Employee', email: 'ava@resolveiq.test', lands: 'Report and track own incidents' },
  ],
}

function renderLogin() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <ThemeProvider>
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/login']}>
          <AuthProvider>
            <Routes>
              <Route path="/login" element={<Login />} />
              <Route path="/" element={<p>Home page</p>} />
            </Routes>
          </AuthProvider>
        </MemoryRouter>
      </QueryClientProvider>
    </ThemeProvider>,
  )
}

describe('Sign-in page', () => {
  it('FE-13 demo accounts are folded away until asked for, then sign in with one click', async () => {
    session.clear()
    const { calls } = fakeApi({
      'GET /api/auth/options': { body: OPTIONS },
      'POST /api/auth/login': { body: sessionBody('tok') },
    })
    const user = userEvent.setup()
    renderLogin()

    const toggle = await screen.findByRole('button', { name: /try a demo account/i })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByText('manager@resolveiq.test')).not.toBeInTheDocument()

    await user.click(toggle)
    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByText('Password@123')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /employee.*ava@resolveiq.test/i }))

    expect(await screen.findByText('Home page')).toBeInTheDocument()
    const login = calls.find((c) => c.url === '/api/auth/login')
    expect(login?.body).toEqual({ email: 'ava@resolveiq.test', password: 'Password@123' })
  })

  it('FE-14 wrong password shows the server message and stays on the page', async () => {
    session.clear()
    fakeApi({
      'GET /api/auth/options': { body: { ...OPTIONS, demoMode: false, demoAccounts: undefined } },
      'POST /api/auth/login': { status: 401, body: { code: 'INVALID_CREDENTIALS', detail: 'Invalid email or password' } },
    })
    const user = userEvent.setup()
    renderLogin()
    await user.type(screen.getByLabelText('Work email'), 'ava@resolveiq.test')
    await user.type(screen.getByLabelText('Password'), 'nope-nope-1')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByText('Invalid email or password')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /try a demo account/i })).not.toBeInTheDocument()
  })

  it('FE-15 with two-step verification on, the password step asks for a 6-digit code next', async () => {
    session.clear()
    const { calls } = fakeApi({
      'GET /api/auth/options': { body: OPTIONS },
      'POST /api/auth/login': { body: { mfaRequired: true, challenge: 'challenge-1' } },
      'POST /api/auth/mfa/verify': { body: sessionBody('tok', { id: 4, fullName: 'Admin', email: 'admin@resolveiq.test', role: 'ADMIN', profileCompleted: true }) },
    })
    const user = userEvent.setup()
    renderLogin()
    await user.type(screen.getByLabelText('Work email'), 'admin@resolveiq.test')
    await user.type(screen.getByLabelText('Password'), 'Tulip-River-42')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('heading', { name: 'Two-step verification' })).toBeInTheDocument()
    const verify = screen.getByRole('button', { name: 'Verify' })
    const code = screen.getByLabelText('Code')
    await user.type(code, '12a34')
    expect(code).toHaveValue('1234')          // digits only
    expect(verify).toBeDisabled()             // until all six are in
    await user.type(code, '56')
    await user.click(verify)

    await waitFor(() => expect(screen.getByText('Home page')).toBeInTheDocument())
    expect(calls.find((c) => c.url === '/api/auth/mfa/verify')?.body).toEqual({ code: '123456', challenge: 'challenge-1' })
  })

  it('FE-16 the Google button explains itself when company sign-in is not configured', async () => {
    session.clear()
    fakeApi({ 'GET /api/auth/options': { body: OPTIONS } })
    renderLogin()
    const google = await screen.findByRole('button', { name: /continue with google/i })
    expect(google).toBeDisabled()
    expect(google).toHaveAccessibleDescription(/not configured/i)
  })
})
