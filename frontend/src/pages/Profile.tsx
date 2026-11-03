import { useEffect, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import QRCode from 'qrcode'
import { toast } from 'sonner'
import { Building2, Copy, Download, KeyRound, Laptop, LogOut, ShieldAlert, ShieldCheck } from 'lucide-react'
import { api, ApiError } from '@/lib/api'
import { useAuth, useUser } from '@/lib/auth'
import { ago, cn, ROLE_LABEL } from '@/lib/format'
import type { AuthResponse, SessionView, User } from '@/lib/types'
import { Avatar, Button, ChipsInput, Dialog, ErrorNote, Field, Input, PageHeader, Panel, Skeleton, Tabs } from '@/components/ui'

type Tab = 'details' | 'security'

export default function Profile() {
  const user = useUser()
  const { mfaSetupRequired } = useAuth()
  const [params, setParams] = useSearchParams()
  const tab = (params.get('tab') as Tab) || (mfaSetupRequired ? 'security' : 'details')

  return (
    <div className="max-w-3xl">
      <PageHeader title="Your profile" description="Your details and how you sign in." />
      {mfaSetupRequired && (
        <div role="alert" className="mb-6 flex gap-3 rounded-xl border border-p2/30 bg-p2-soft px-4 py-3 text-[13px] text-ink">
          <ShieldAlert className="mt-0.5 size-4 shrink-0 text-p2" />
          <span>Your organisation requires two-step verification for {ROLE_LABEL[user.role].toLowerCase()} accounts. Set it up below to continue.</span>
        </div>
      )}
      <Tabs label="Profile sections" value={tab} onChange={(v) => setParams({ tab: v })}
        tabs={[{ value: 'details', label: 'Details' }, { value: 'security', label: 'Sign-in & security' }]} />
      <div className="mt-6 space-y-6">
        {tab === 'details' ? <DetailsSection /> : (<><SignInMethods /><PasswordSection /><MfaSection /><SessionsSection /></>)}
      </div>
    </div>
  )
}

// ---------------------------------------------------------------------------------------------

function DetailsSection() {
  const user = useUser()
  const { setUser } = useAuth()
  const [form, setForm] = useState({ fullName: user.fullName, phone: user.phone ?? '', department: user.department ?? '', location: user.location ?? '' })
  const [skills, setSkills] = useState(user.technician?.skills ?? [])
  const save = useMutation({
    mutationFn: () => api.put<User>('/auth/profile', { ...form, skills: user.technician ? skills : undefined }),
    onSuccess: (u) => { setUser(u); toast.success('Profile saved') },
  })
  const fieldErrors = save.error instanceof ApiError ? save.error.fieldErrors ?? {} : {}
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [k]: e.target.value })

  return (
    <Panel>
      <form onSubmit={(e) => { e.preventDefault(); save.mutate() }} className="space-y-5">
        <div className="flex items-center gap-4">
          <Avatar name={user.fullName} src={user.avatarUrl} size={56} />
          <div className="min-w-0">
            <p className="truncate font-medium">{user.email}</p>
            <p className="text-[13px] text-muted">{ROLE_LABEL[user.role]} · set by your IT administrator</p>
          </div>
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Full name" htmlFor="p-name" error={fieldErrors.fullName}>
            <Input id="p-name" value={form.fullName} onChange={set('fullName')} required autoComplete="name" />
          </Field>
          <Field label="Mobile number" htmlFor="p-phone" hint="For urgent SMS alerts" error={fieldErrors.phone}>
            <Input id="p-phone" value={form.phone} onChange={set('phone')} autoComplete="tel" />
          </Field>
          <Field label="Department" htmlFor="p-dept">
            <Input id="p-dept" value={form.department} onChange={set('department')} />
          </Field>
          <Field label="Location" htmlFor="p-loc">
            <Input id="p-loc" value={form.location} onChange={set('location')} />
          </Field>
        </div>
        {user.technician && (
          <Field label="Skills" htmlFor="p-skills" hint="Smart assignment routes incidents to you by these.">
            <ChipsInput id="p-skills" value={skills} onChange={setSkills} />
          </Field>
        )}
        {save.error && !Object.keys(fieldErrors).length ? <ErrorNote error={save.error} /> : null}
        <div className="flex justify-end"><Button type="submit" variant="primary" loading={save.isPending}>Save changes</Button></div>
      </form>
    </Panel>
  )
}

function SignInMethods() {
  const user = useUser()
  return (
    <Panel title="Ways to sign in">
      <ul className="divide-y divide-line rounded-lg border border-line">
        <li className="flex items-center gap-3 px-4 py-3">
          <Building2 className="size-4 text-muted" aria-hidden />
          <span className="flex-1 text-[13.5px]">Company account (Google)</span>
          <span className={cn('text-xs font-medium', user.googleLinked ? 'text-ok' : 'text-faint')}>{user.googleLinked ? 'Linked' : 'Not linked yet'}</span>
        </li>
        <li className="flex items-center gap-3 px-4 py-3">
          <KeyRound className="size-4 text-muted" aria-hidden />
          <span className="flex-1 text-[13.5px]">Password</span>
          <span className={cn('text-xs font-medium', user.passwordEnabled ? 'text-ok' : 'text-faint')}>{user.passwordEnabled ? 'On' : 'Not set'}</span>
        </li>
      </ul>
      {!user.googleLinked && <p className="mt-2 text-xs text-muted">Signing in with Google once links your company account automatically.</p>}
    </Panel>
  )
}

function PasswordSection() {
  const user = useUser()
  const { accept } = useAuth()
  const qc = useQueryClient()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const change = useMutation({
    mutationFn: () => api.post<AuthResponse>('/auth/change-password', { currentPassword: current, newPassword: next }),
    onSuccess: (res) => {
      accept(res)
      setCurrent('')
      setNext('')
      qc.invalidateQueries({ queryKey: ['sessions'] })
      toast.success(user.passwordEnabled ? 'Password changed. Other devices were signed out.' : 'Password set. You can now sign in with it too.')
    },
  })
  return (
    <Panel title={user.passwordEnabled ? 'Change password' : 'Set a password'}
      description={user.passwordEnabled ? 'Changing it signs you out on every other device.' : 'Optional: lets you sign in without Google.'}>
      <form onSubmit={(e: FormEvent) => { e.preventDefault(); change.mutate() }} className="grid gap-4 sm:grid-cols-2">
        {user.passwordEnabled && (
          <Field label="Current password" htmlFor="pw-cur">
            <Input id="pw-cur" type="password" required autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} />
          </Field>
        )}
        <Field label="New password" htmlFor="pw-new" hint="8+ characters, a letter and a digit">
          <Input id="pw-new" type="password" required minLength={8} autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} />
        </Field>
        {change.error ? <div className="sm:col-span-2"><ErrorNote error={change.error instanceof ApiError && change.error.code === 'INVALID_CREDENTIALS' ? new Error('Your current password is not correct.') : change.error} /></div> : null}
        <div className="flex justify-end sm:col-span-2"><Button type="submit" loading={change.isPending}>{user.passwordEnabled ? 'Change password' : 'Set password'}</Button></div>
      </form>
    </Panel>
  )
}

// ---------------------------------------------------------------------------------------------
// Two-step verification
// ---------------------------------------------------------------------------------------------

function MfaSection() {
  const user = useUser()
  const { refresh } = useAuth()
  const status = useQuery({ queryKey: ['mfa'], queryFn: () => api.get<{ enabled: boolean; recoveryCodesLeft: number; required: boolean }>('/auth/mfa') })
  const [setupOpen, setSetupOpen] = useState(false)
  const [disableOpen, setDisableOpen] = useState(false)
  const [codes, setCodes] = useState<string[] | null>(null)
  const [regenOpen, setRegenOpen] = useState(false)

  const enabled = status.data?.enabled ?? user.mfaEnabled
  return (
    <Panel
      title="Two-step verification"
      description="After your password or Google, enter a code from an authenticator app (Google Authenticator, Microsoft Authenticator, 1Password…)."
      action={status.data && (
        <span className={cn('inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium', enabled ? 'bg-ok-soft text-ok' : 'bg-surface-2 text-muted')}>
          {enabled ? <ShieldCheck className="size-3.5" /> : <ShieldAlert className="size-3.5" />}{enabled ? 'On' : 'Off'}
        </span>
      )}
    >
      {status.isLoading ? <Skeleton className="h-9 w-40" /> : enabled ? (
        <div className="flex flex-wrap items-center gap-2">
          <p className="mr-auto text-[13px] text-muted">{status.data?.recoveryCodesLeft ?? 0} unused recovery codes left.</p>
          <Button size="sm" onClick={() => setRegenOpen(true)}>New recovery codes</Button>
          {!status.data?.required && <Button size="sm" variant="ghost" onClick={() => setDisableOpen(true)}>Turn off</Button>}
        </div>
      ) : (
        <Button variant="primary" icon={<ShieldCheck className="size-4" />} onClick={() => setSetupOpen(true)}>Set up two-step verification</Button>
      )}

      {setupOpen && <MfaSetupDialog onClose={() => setSetupOpen(false)} onDone={async (c) => { setSetupOpen(false); setCodes(c); await refresh(); status.refetch() }} />}
      {disableOpen && <MfaDisableDialog onClose={() => setDisableOpen(false)} onDone={async () => { setDisableOpen(false); await refresh(); status.refetch() }} />}
      {regenOpen && <RegenerateDialog onClose={() => setRegenOpen(false)} onDone={(c) => { setRegenOpen(false); setCodes(c); status.refetch() }} />}
      {codes && <RecoveryCodesDialog codes={codes} onClose={() => setCodes(null)} />}
    </Panel>
  )
}

function MfaSetupDialog({ onClose, onDone }: { onClose: () => void; onDone: (codes: string[]) => void }) {
  const [qr, setQr] = useState<string | null>(null)
  const [code, setCode] = useState('')
  const setup = useQuery({ queryKey: ['mfa-setup'], queryFn: () => api.post<{ secret: string; otpauthUri: string }>('/auth/mfa/setup'), staleTime: Infinity, gcTime: 0, retry: false })
  useEffect(() => {
    if (setup.data) QRCode.toDataURL(setup.data.otpauthUri, { margin: 1, width: 200, errorCorrectionLevel: 'M' }).then(setQr)
  }, [setup.data])
  const enable = useMutation({
    mutationFn: () => api.post<{ recoveryCodes: string[] }>('/auth/mfa/enable', { code }),
    onSuccess: (r) => onDone(r.recoveryCodes),
  })
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()} title="Set up two-step verification" width={460}
      footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button variant="primary" loading={enable.isPending} disabled={code.length !== 6} onClick={() => enable.mutate()}>Turn on</Button></>}>
      {setup.error ? <ErrorNote error={setup.error} /> : (
        <ol className="space-y-5 text-[13px]">
          <li>
            <p className="font-medium">1. Scan this QR code with your authenticator app</p>
            <div className="mt-3 flex items-center gap-4">
              <div className="flex size-[168px] shrink-0 items-center justify-center rounded-lg border border-line bg-white p-2">
                {qr ? <img src={qr} alt="QR code for your authenticator app" className="size-full" /> : <Skeleton className="size-full" />}
              </div>
              <div className="min-w-0 text-muted">
                <p>Can't scan? Enter this key:</p>
                <p className="mt-1 break-all rounded-md bg-surface-2 px-2 py-1.5 font-mono text-[12px] text-ink">{setup.data?.secret.replace(/(.{4})/g, '$1 ').trim()}</p>
              </div>
            </div>
          </li>
          <li>
            <label htmlFor="mfa-new" className="font-medium">2. Enter the 6-digit code it shows</label>
            <Input id="mfa-new" className="mt-2 w-40 text-center text-lg tracking-[0.35em] tabular" inputMode="numeric" autoComplete="one-time-code"
              value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))} placeholder="123456" />
          </li>
          {enable.error ? <ErrorNote error={enable.error} /> : null}
        </ol>
      )}
    </Dialog>
  )
}

function MfaDisableDialog({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const user = useUser()
  const [password, setPassword] = useState('')
  const [code, setCode] = useState('')
  const disable = useMutation({ mutationFn: () => api.post('/auth/mfa/disable', { password, code }), onSuccess: onDone })
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()} title="Turn off two-step verification"
      description="Your account will be protected by your password (or Google) only."
      footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button variant="danger" loading={disable.isPending} onClick={() => disable.mutate()}>Turn off</Button></>}>
      <div className="space-y-4">
        {user.passwordEnabled ? (
          <Field label="Confirm with your password" htmlFor="mfa-off-pw">
            <Input id="mfa-off-pw" type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" />
          </Field>
        ) : (
          <Field label="Confirm with a current code" htmlFor="mfa-off-code">
            <Input id="mfa-off-code" inputMode="numeric" value={code} onChange={(e) => setCode(e.target.value)} autoComplete="one-time-code" />
          </Field>
        )}
        {disable.error ? <ErrorNote error={disable.error} /> : null}
      </div>
    </Dialog>
  )
}

function RegenerateDialog({ onClose, onDone }: { onClose: () => void; onDone: (codes: string[]) => void }) {
  const [code, setCode] = useState('')
  const regen = useMutation({ mutationFn: () => api.post<{ recoveryCodes: string[] }>('/auth/mfa/recovery-codes', { code }), onSuccess: (r) => onDone(r.recoveryCodes) })
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()} title="New recovery codes" description="Your old recovery codes will stop working."
      footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button variant="primary" loading={regen.isPending} onClick={() => regen.mutate()}>Create new codes</Button></>}>
      <Field label="Code from your authenticator app" htmlFor="regen-code">
        <Input id="regen-code" inputMode="numeric" autoComplete="one-time-code" value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))} />
      </Field>
      {regen.error ? <div className="mt-3"><ErrorNote error={regen.error} /></div> : null}
    </Dialog>
  )
}

function RecoveryCodesDialog({ codes, onClose }: { codes: string[]; onClose: () => void }) {
  const text = codes.join('\n')
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()} title="Save your recovery codes"
      description="Each code signs you in once if you lose your phone. They are shown only now."
      footer={<Button variant="primary" onClick={onClose}>I've saved them</Button>}>
      <ul className="grid grid-cols-2 gap-2 rounded-lg bg-surface-2 p-4 font-mono text-[13px]">
        {codes.map((c) => <li key={c} className="tabular">{c}</li>)}
      </ul>
      <div className="mt-3 flex gap-2">
        <Button size="sm" icon={<Copy className="size-3.5" />} onClick={() => { navigator.clipboard?.writeText(text); toast.success('Copied') }}>Copy</Button>
        <Button size="sm" icon={<Download className="size-3.5" />} onClick={() => {
          const url = URL.createObjectURL(new Blob([`ResolveIQ recovery codes\n\n${text}\n`], { type: 'text/plain' }))
          const a = document.createElement('a'); a.href = url; a.download = 'resolveiq-recovery-codes.txt'; a.click(); URL.revokeObjectURL(url)
        }}>Download</Button>
      </div>
    </Dialog>
  )
}

// ---------------------------------------------------------------------------------------------

function SessionsSection() {
  const qc = useQueryClient()
  const sessions = useQuery({ queryKey: ['sessions'], queryFn: () => api.get<SessionView[]>('/auth/sessions') })
  const revoke = useMutation({
    mutationFn: (id: string) => api.delete(`/auth/sessions/${id}`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['sessions'] }); toast.success('Signed out that device') },
  })
  const others = useMutation({
    mutationFn: () => api.post<{ signedOut: number }>('/auth/sessions/revoke-others'),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['sessions'] }); toast.success('Signed out everywhere else') },
  })
  const list = sessions.data ?? []
  return (
    <Panel title="Where you're signed in" description="Sessions end after 7 days without activity. Sign out anything you don't recognise."
      action={list.length > 1 && <Button size="sm" icon={<LogOut className="size-3.5" />} loading={others.isPending} onClick={() => others.mutate()}>Sign out other devices</Button>}>
      {sessions.isLoading ? <Skeleton className="h-16" /> : (
        <ul className="divide-y divide-line rounded-lg border border-line">
          {list.map((s) => (
            <li key={s.id} className="flex items-center gap-3 px-4 py-3">
              <Laptop className="size-4 shrink-0 text-muted" aria-hidden />
              <div className="min-w-0 flex-1">
                <p className="flex items-center gap-2 text-[13.5px] font-medium">
                  {s.device}
                  {s.current && <span className="rounded-full bg-accent-soft px-2 py-px text-[11px] font-medium text-accent-text">This device</span>}
                </p>
                <p className="text-xs text-muted">{s.method} · {s.ipAddress ?? 'unknown address'} · active {ago(s.lastActiveAt)}</p>
              </div>
              {!s.current && <Button size="sm" variant="ghost" loading={revoke.isPending && revoke.variables === s.id} onClick={() => revoke.mutate(s.id)}>Sign out</Button>}
            </li>
          ))}
        </ul>
      )}
    </Panel>
  )
}
