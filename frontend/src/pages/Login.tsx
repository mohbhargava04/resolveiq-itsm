import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, Building2, ChevronDown, KeyRound, LockKeyhole, ShieldCheck } from 'lucide-react'
import { useAuth } from '@/lib/auth'
import { api, ApiError } from '@/lib/api'
import { cn, ROLE_LABEL } from '@/lib/format'
import { PROJECT } from '@/lib/project'
import type { AuthOptions, DemoAccount, Role } from '@/lib/types'
import { Button, ErrorNote, Field, Input } from '@/components/ui'
import { Logo, ThemeMenu } from '@/components/shell'

/** Why a company-account sign-in came back to this page (the server redirects with ?error=). */
const SSO_ERRORS: Record<string, string> = {
  domain_not_allowed: 'That Google account is not a company account. Sign in with your work address.',
  not_provisioned: 'There is no ResolveIQ account for that address yet. Ask your IT administrator to add you.',
  account_disabled: 'This account has been deactivated. Contact your IT administrator.',
  account_conflict: 'This ResolveIQ account is linked to a different Google account.',
  email_unverified: 'Google has not verified that e-mail address.',
  cancelled: 'Google sign-in was cancelled.',
  expired: 'Sign-in took too long. Please try again.',
  state_mismatch: 'Sign-in could not be verified. Please try again.',
  provider_disabled: 'Google sign-in is not set up on this server.',
  provider_error: 'Google could not complete the sign-in. Please try again.',
  invalid_token: 'Google could not complete the sign-in. Please try again.',
}

const ROLE_ORDER: Role[] = ['EMPLOYEE', 'TECHNICIAN', 'TEAM_LEAD', 'ADMIN', 'MANAGER']

export default function Login() {
  const { user, login, verifyMfa } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [params, setParams] = useSearchParams()
  const options = useQuery({ queryKey: ['auth-options'], queryFn: () => api.get<AuthOptions>('/auth/options'), staleTime: 60_000 })

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState<string | null>(null)
  const [error, setError] = useState<unknown>(null)
  // two-factor step: challenge from the password step, or held in a cookie after Google sign-in
  const [mfa, setMfa] = useState<{ challenge?: string } | null>(params.get('step') === 'mfa' ? {} : null)
  const ssoError = params.get('error')

  if (user) return <Navigate to={user.profileCompleted ? '/' : '/welcome'} replace />
  const from = (location.state as { from?: string } | null)?.from ?? '/'

  const signIn = async (e: string, p: string, key: string) => {
    setBusy(key)
    setError(null)
    try {
      const result = await login(e, p)
      if (result.kind === 'mfa') setMfa({ challenge: result.challenge })
      else navigate(result.user.profileCompleted ? from : '/welcome', { replace: true })
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

  const google = options.data?.providers.find((p) => p.id === 'google')
  const domains = options.data?.allowedDomains ?? []

  return (
    <div className="grid min-h-dvh lg:grid-cols-2">
      {/* ---------------------------------------------------------------- brand & project ---- */}
      <aside className="relative order-2 overflow-hidden border-line bg-surface lg:order-1 lg:border-r">
        <div aria-hidden className="pointer-events-none absolute inset-0 login-dots" />
        <div className="relative flex h-full flex-col px-6 py-8 sm:px-12 lg:py-10">
          <div className="hidden lg:block"><Logo /></div>

          <div className="mx-auto flex w-full max-w-[460px] flex-1 flex-col justify-center gap-9 py-6 lg:py-12">
            <div className="hidden lg:block">
              <p className="text-[13px] font-medium text-accent-text">{PROJECT.tagline}</p>
              <h2 className="mt-2 text-[30px] font-semibold leading-[1.12] tracking-tight text-balance">
                Every incident, from the first report to a confirmed fix.
              </h2>
            </div>

            <FuseIllustration />

            <ProjectBlock />

            {options.data?.demoMode && options.data.demoAccounts && (
              <DemoAccounts
                accounts={options.data.demoAccounts}
                password={options.data.demoPassword ?? ''}
                busy={busy}
                onPick={(a) => signIn(a.email, options.data!.demoPassword ?? '', a.email)}
              />
            )}
          </div>
        </div>
      </aside>

      {/* ---------------------------------------------------------------- sign in ------------- */}
      <main className="order-1 flex flex-col px-6 py-8 sm:px-12 lg:order-2 lg:py-10">
        <div className="flex items-center justify-between">
          <div className="lg:invisible"><Logo /></div>
          <ThemeMenu />
        </div>

        <div className="mx-auto flex w-full max-w-[360px] flex-1 flex-col justify-center py-12">
          {mfa ? (
            <MfaStep
              challenge={mfa.challenge}
              onBack={() => { setMfa(null); setError(null); setParams({}) }}
              onVerify={async (code) => {
                const u = await verifyMfa(code, mfa.challenge)
                navigate(u.profileCompleted ? from : '/welcome', { replace: true })
              }}
            />
          ) : (
            <>
              <h1 className="text-[28px] font-semibold leading-tight tracking-tight">Sign in</h1>
              <p className="mt-2 text-sm text-muted">
                {domains.length === 1
                  ? <>Use your <span className="font-medium text-ink-2">@{domains[0]}</span> company account.</>
                  : 'Use your company account.'}
              </p>

              {ssoError && (
                <div className="mt-6"><ErrorNote error={new Error(SSO_ERRORS[ssoError] ?? 'Sign-in failed. Please try again.')} /></div>
              )}

              <div className="mt-7">
                {google ? (
                  <a
                    href={`/api/auth/oauth/google/start?returnTo=${encodeURIComponent(from)}`}
                    className="flex h-10 w-full items-center justify-center gap-2.5 rounded-lg border border-line-strong bg-surface text-sm font-medium text-ink transition-colors hover:bg-surface-2"
                  >
                    <Building2 className="size-4 text-accent-text" aria-hidden />
                    Continue with Google
                  </a>
                ) : (
                  <div>
                    <button
                      type="button"
                      disabled
                      aria-describedby="google-off"
                      className="flex h-10 w-full cursor-not-allowed items-center justify-center gap-2.5 rounded-lg border border-dashed border-line-strong text-sm font-medium text-faint"
                    >
                      <Building2 className="size-4" aria-hidden />
                      Continue with Google
                    </button>
                    <p id="google-off" className="mt-1.5 text-center text-xs text-faint">Company sign-in is not configured on this server yet.</p>
                  </div>
                )}
              </div>

              <div className="my-6 flex items-center gap-3 text-xs text-faint">
                <span className="h-px flex-1 bg-line" />or with your password<span className="h-px flex-1 bg-line" />
              </div>

              <form onSubmit={submit} className="space-y-4" noValidate>
                <Field label="Work email" htmlFor="email">
                  <Input id="email" type="email" autoComplete="username" required value={email}
                    onChange={(e) => setEmail(e.target.value)} placeholder={domains[0] ? `you@${domains[0]}` : 'you@company.com'} />
                </Field>
                <div className="flex flex-col gap-1.5">
                  <div className="flex items-baseline justify-between">
                    <label htmlFor="password" className="text-[13px] font-medium text-ink-2">Password</label>
                    <Link to="/forgot-password" className="text-xs font-medium text-accent-text hover:underline">Forgot password?</Link>
                  </div>
                  <Input id="password" type="password" autoComplete="current-password" required value={password}
                    onChange={(e) => setPassword(e.target.value)} />
                </div>
                {error ? <ErrorNote error={explain(error)} /> : null}
                <Button type="submit" variant="primary" className="w-full" loading={busy === 'form'}>Sign in</Button>
              </form>

              {options.data?.selfRegistration && (
                <p className="mt-6 text-[13px] text-muted">
                  New employee? <Link to="/register" className="font-medium text-accent-text hover:underline">Create an account</Link>
                </p>
              )}
            </>
          )}
        </div>

        <p className="flex items-center gap-1.5 text-xs text-faint">
          <LockKeyhole className="size-3.5" aria-hidden />
          Sessions are encrypted, expire after inactivity, and can be signed out from your profile.
        </p>
      </main>
    </div>
  )
}

function explain(error: unknown): unknown {
  if (error instanceof ApiError) {
    if (error.code === 'ACCOUNT_LOCKED') return new Error('Too many wrong attempts. This account is locked for 15 minutes.')
    if (error.code === 'RATE_LIMITED') return new Error('Too many sign-in attempts from this network. Wait a minute and try again.')
    if (error.status === 400) return new Error('Enter your work e-mail and password.')
  }
  return error
}

// ---------------------------------------------------------------------------------------------
// Two-factor step
// ---------------------------------------------------------------------------------------------

function MfaStep({ challenge, onVerify, onBack }: { challenge?: string; onVerify: (code: string) => Promise<void>; onBack: () => void }) {
  const [code, setCode] = useState('')
  const [recovery, setRecovery] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const input = useRef<HTMLInputElement>(null)
  useEffect(() => { input.current?.focus() }, [recovery])

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await onVerify(code)
    } catch (err) {
      setError(err instanceof ApiError && err.code === 'SESSION_EXPIRED'
        ? new Error('That sign-in has expired. Go back and sign in again.')
        : err instanceof ApiError && err.code === 'ACCOUNT_LOCKED'
          ? new Error('Too many wrong codes. This account is locked for 15 minutes.') : err)
      setCode('')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div>
      <button onClick={onBack} className="mb-8 inline-flex items-center gap-1.5 text-[13px] text-muted hover:text-ink cursor-pointer">
        <ArrowLeft className="size-4" /> Back
      </button>
      <span className="inline-flex size-10 items-center justify-center rounded-full bg-accent-soft text-accent-text"><ShieldCheck className="size-5" /></span>
      <h1 className="mt-4 text-[26px] font-semibold leading-tight tracking-tight">Two-step verification</h1>
      <p className="mt-2 text-sm text-muted">
        {recovery ? 'Enter one of the recovery codes you saved when you set up two-step verification.'
          : 'Enter the 6-digit code from your authenticator app.'}
      </p>
      <form onSubmit={submit} className="mt-7 space-y-4">
        <Field label={recovery ? 'Recovery code' : 'Code'} htmlFor="mfa-code">
          <Input
            ref={input}
            id="mfa-code"
            value={code}
            onChange={(e) => setCode(recovery ? e.target.value.toUpperCase() : e.target.value.replace(/\D/g, '').slice(0, 6))}
            inputMode={recovery ? 'text' : 'numeric'}
            autoComplete="one-time-code"
            placeholder={recovery ? 'XXXXX-XXXXX' : '123456'}
            className={cn(!recovery && 'text-center text-lg tracking-[0.4em] tabular')}
            required
          />
        </Field>
        {error ? <ErrorNote error={error} /> : null}
        <Button type="submit" variant="primary" className="w-full" loading={busy} disabled={!recovery && code.length !== 6}>Verify</Button>
      </form>
      <button onClick={() => { setRecovery(!recovery); setCode(''); setError(null) }}
        className="mt-5 inline-flex items-center gap-1.5 text-[13px] font-medium text-accent-text hover:underline cursor-pointer">
        <KeyRound className="size-3.5" /> {recovery ? 'Use the authenticator app instead' : 'Use a recovery code instead'}
      </button>
      {!challenge && <p className="sr-only">Continuing a company-account sign-in.</p>}
    </div>
  )
}

// ---------------------------------------------------------------------------------------------
// Left panel
// ---------------------------------------------------------------------------------------------

/** The product's signature element: SLA fuses burning down as each incident's resolution window is used. */
function FuseIllustration() {
  const rows = [
    { id: 'INC-1042', title: 'Campus Wi-Fi down in the library', p: 'P1', tone: 'p1', from: 62, to: 91 },
    { id: 'INC-1038', title: 'HR portal timing out on leave page', p: 'P2', tone: 'p2', from: 38, to: 71 },
    { id: 'INC-1031', title: 'Printer on the 2nd floor jammed', p: 'P4', tone: 'accent', from: 12, to: 34 },
  ] as const
  return (
    <figure className="rounded-xl border border-line bg-bg/70 p-4 shadow-[0_1px_0_rgb(0_0_0/0.02)] backdrop-blur-sm" aria-label="Illustration: incidents with their SLA fuses">
      <div className="mb-3 flex items-center justify-between text-[11px] font-medium text-faint">
        <span>Live queue</span>
        <span className="inline-flex items-center gap-1.5"><span className="size-1.5 animate-pulse rounded-full bg-ok" /> SLA monitor</span>
      </div>
      <ul className="space-y-3.5">
        {rows.map((r, i) => (
          <li key={r.id}>
            <div className="flex items-center gap-2 text-[12.5px]">
              <span className={cn('rounded px-1.5 py-px text-[10.5px] font-semibold',
                r.tone === 'p1' ? 'bg-p1 text-p1-ink' : r.tone === 'p2' ? 'bg-p2-soft text-p2' : 'bg-p4-soft text-p4')}>{r.p}</span>
              <span className="tabular text-faint">{r.id}</span>
              <span className="truncate text-ink-2">{r.title}</span>
            </div>
            <div className="mt-1.5 h-1 overflow-hidden rounded-full bg-surface-3">
              <span
                className={cn('fuse-burn block h-full rounded-full', r.tone === 'p1' ? 'bg-p1' : r.tone === 'p2' ? 'bg-p2' : 'bg-accent')}
                style={{ ['--fuse-from' as string]: `${r.from}%`, ['--fuse-to' as string]: `${r.to}%`, animationDelay: `${i * -2.2}s` }}
              />
            </div>
          </li>
        ))}
        <li>
          <div className="flex items-center gap-2 text-[12.5px]">
            <span className="rounded bg-ok-soft px-1.5 py-px text-[10.5px] font-semibold text-ok">Met</span>
            <span className="tabular text-faint">INC-1029</span>
            <span className="truncate text-ink-2">VPN drops when working from home</span>
          </div>
          <div className="mt-1.5 h-1 rounded-full bg-ok/80" />
        </li>
      </ul>
    </figure>
  )
}

function ProjectBlock() {
  return (
    <section aria-label="About this project">
      <div className="h-px bg-line" />
      <div className="mt-6 flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
        <p className="text-[13px] font-medium text-ink-2">{PROJECT.course}</p>
        <p className={cn('text-[13px]', PROJECT.groupPlaceholder ? 'italic text-faint' : 'font-medium text-ink-2')}>{PROJECT.group}</p>
      </div>
      <p className="mt-0.5 text-xs text-muted">Group project · {PROJECT.term}</p>
      <ul className="mt-4 grid grid-cols-2 gap-x-6 gap-y-2.5">
        {PROJECT.team.map((m) => (
          <li key={m.name + m.id} className="min-w-0">
            <span className={cn('block truncate text-[13px]', m.placeholder ? 'italic text-faint' : 'font-medium text-ink')}>{m.name}</span>
            <span className={cn('block truncate text-xs tabular', m.placeholder ? 'italic text-faint' : 'text-muted')}>{m.id}</span>
          </li>
        ))}
      </ul>
    </section>
  )
}

function DemoAccounts({ accounts, password, busy, onPick }: {
  accounts: DemoAccount[]; password: string; busy: string | null; onPick: (a: DemoAccount) => void
}) {
  const [open, setOpen] = useState(() => {
    try { return localStorage.getItem('riq-demo-open') === '1' } catch { return false }
  })
  const toggle = () => {
    setOpen(!open)
    try { localStorage.setItem('riq-demo-open', open ? '0' : '1') } catch { /* ignore */ }
  }
  const sorted = [...accounts].sort((a, b) => ROLE_ORDER.indexOf(a.role) - ROLE_ORDER.indexOf(b.role))
  return (
    <section className="rounded-xl border border-line bg-bg/70 backdrop-blur-sm">
      <button
        onClick={toggle}
        aria-expanded={open}
        aria-controls="demo-accounts"
        className="flex w-full items-center justify-between gap-3 px-4 py-3 text-left cursor-pointer"
      >
        <span>
          <span className="block text-[13.5px] font-semibold">Try a demo account</span>
          <span className="block text-xs text-muted">One click signs you in at that access level.</span>
        </span>
        <ChevronDown className={cn('size-4 shrink-0 text-muted transition-transform', open && 'rotate-180')} aria-hidden />
      </button>
      {open && (
        <div id="demo-accounts" className="border-t border-line">
          <ul className="divide-y divide-line">
            {sorted.map((a) => (
              <li key={a.email}>
                <button
                  onClick={() => onPick(a)}
                  disabled={!!busy}
                  className={cn('group flex w-full items-center gap-3 px-4 py-2.5 text-left transition-colors hover:bg-surface-2 cursor-pointer disabled:cursor-wait',
                    busy === a.email && 'bg-surface-2')}
                >
                  <RoleDot role={a.role} />
                  <span className="min-w-0 flex-1">
                    <span className="flex items-baseline gap-2">
                      <span className="text-[13px] font-medium">{ROLE_LABEL[a.role]}</span>
                      <span className="truncate text-xs text-faint">{a.email}</span>
                    </span>
                    <span className="mt-px block truncate text-xs text-muted">{a.lands}</span>
                  </span>
                  <span className="text-xs font-medium text-accent-text opacity-70 group-hover:opacity-100">
                    {busy === a.email ? 'Signing in…' : 'Sign in'}
                  </span>
                </button>
              </li>
            ))}
          </ul>
          <p className="border-t border-line px-4 py-2.5 text-xs text-muted">
            Password for every demo account: <span className="font-medium tabular text-ink-2">{password}</span>
          </p>
        </div>
      )}
    </section>
  )
}

/** Clearance level as a small stacked marker: one bar per level. */
function RoleDot({ role }: { role: Role }) {
  const level = ROLE_ORDER.indexOf(role) + 1
  return (
    <span aria-hidden className="flex h-5 w-5 shrink-0 items-end justify-center gap-[2px]">
      {[1, 2, 3, 4, 5].map((l) => (
        <span key={l} className={cn('w-[3px] rounded-sm', l <= level ? 'bg-accent' : 'bg-line-strong')} style={{ height: `${6 + l * 2.6}px` }} />
      ))}
    </span>
  )
}
