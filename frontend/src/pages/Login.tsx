import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '@/lib/auth'
import { ApiError } from '@/lib/api'
import { cn, ROLE_LABEL } from '@/lib/format'
import type { Role } from '@/lib/types'
import { Avatar, Button, ErrorNote, Field, Input } from '@/components/ui'
import { Logo } from '@/components/shell'

const DEMO_PASSWORD = 'Password@123'

/** The seeded demo organisation, so evaluators can step into every role in one click. */
const DEMO: { name: string; email: string; role: Role; what: string }[] = [
  { name: 'Ava Sharma', email: 'ava@resolveiq.test', role: 'EMPLOYEE', what: 'Has an incident waiting for her answer and one to verify' },
  { name: 'Rahul Verma', email: 'rahul@resolveiq.test', role: 'TECHNICIAN', what: 'Owns the Wi-Fi P1 he has not acknowledged yet' },
  { name: 'Neha Joshi', email: 'neha@resolveiq.test', role: 'TEAM_LEAD', what: 'Runs the service desk; sees the command centre' },
  { name: 'Ananya Mehta', email: 'admin@resolveiq.test', role: 'ADMIN', what: 'Configures services, SLAs and automation' },
  { name: 'Sanjay Rao', email: 'manager@resolveiq.test', role: 'MANAGER', what: 'Top of the escalation chain' },
]

export default function Login() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState<string | null>(null)
  const [error, setError] = useState<unknown>(null)

  if (user) return <Navigate to="/" replace />
  const from = (location.state as { from?: string } | null)?.from ?? '/'

  const signIn = async (e: string, p: string, key: string) => {
    setBusy(key)
    setError(null)
    try {
      await login(e, p)
      navigate(from, { replace: true })
    } catch (err) {
      setError(err)
    } finally {
      setBusy(null)
    }
  }

  const submit = (ev: FormEvent) => {
    ev.preventDefault()
    signIn(email, password, 'form')
  }

  const locked = error instanceof ApiError && error.code === 'ACCOUNT_LOCKED'

  return (
    <div className="grid min-h-dvh lg:grid-cols-[minmax(0,1fr)_minmax(0,1.05fr)]">
      <div className="flex flex-col px-6 py-8 sm:px-12">
        <Logo />
        <div className="mx-auto flex w-full max-w-sm flex-1 flex-col justify-center py-12">
          <h1 className="text-[28px] font-semibold leading-tight tracking-tight">Sign in to the service desk</h1>
          <p className="mt-2 text-sm text-muted">Report a problem, follow its progress, or pick up your queue.</p>

          <form onSubmit={submit} className="mt-8 space-y-4">
            <Field label="Work email" htmlFor="email">
              <Input id="email" type="email" autoComplete="username" required value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@resolveiq.test" />
            </Field>
            <Field label="Password" htmlFor="password">
              <Input id="password" type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} />
            </Field>
            {error ? (
              <ErrorNote error={locked ? new Error('Too many wrong passwords. This account is locked for 15 minutes.') : error} />
            ) : null}
            <Button type="submit" variant="primary" className="w-full" loading={busy === 'form'}>Sign in</Button>
          </form>
          <p className="mt-6 text-[13px] text-muted">
            New employee? <Link to="/register" className="font-medium text-accent-text hover:underline">Create an account</Link>
          </p>
        </div>
        <p className="text-xs text-faint">CS F213 Object-Oriented Programming project, 2026–27</p>
      </div>

      <div className="hidden border-l border-line bg-surface lg:flex lg:flex-col lg:justify-center lg:px-14">
        <div className="max-w-md">
          <h2 className="text-[15px] font-semibold">Explore as anyone in the demo organisation</h2>
          <p className="mt-1 text-[13px] text-muted">
            Every account uses the password <span className="font-medium text-ink-2">{DEMO_PASSWORD}</span>. Each role sees a different workspace.
          </p>
          <ul className="mt-6 divide-y divide-line rounded-xl border border-line">
            {DEMO.map((d) => (
              <li key={d.email}>
                <button
                  onClick={() => signIn(d.email, DEMO_PASSWORD, d.email)}
                  disabled={!!busy}
                  className={cn('flex w-full items-center gap-3 px-4 py-3 text-left transition-colors hover:bg-surface-2 cursor-pointer disabled:cursor-wait', busy === d.email && 'bg-surface-2')}
                >
                  <Avatar name={d.name} size={34} />
                  <span className="min-w-0 flex-1">
                    <span className="flex items-baseline gap-2">
                      <span className="text-[13.5px] font-medium">{d.name}</span>
                      <span className="text-xs text-muted">{ROLE_LABEL[d.role]}</span>
                    </span>
                    <span className="mt-0.5 block truncate text-xs text-muted">{d.what}</span>
                  </span>
                  <span className="text-xs font-medium text-accent-text">{busy === d.email ? 'Signing in…' : 'Sign in'}</span>
                </button>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </div>
  )
}
