import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { FileUp, KeyRound, LockOpen, LogOut, Plus, Search, ShieldCheck, ShieldOff } from 'lucide-react'
import { api, ApiError } from '@/lib/api'
import { useUser } from '@/lib/auth'
import { ago, cn, ROLE_LABEL } from '@/lib/format'
import type { CategoryView, Role, User } from '@/lib/types'
import { Avatar, Button, ChipsInput, Dialog, ErrorNote, Field, Input, PageHeader, Panel, Segmented, Select, Skeleton, Switch, Textarea } from '@/components/ui'

const ROLES: Role[] = ['EMPLOYEE', 'TECHNICIAN', 'TEAM_LEAD', 'ADMIN', 'MANAGER']
const LEVELS = ['JUNIOR', 'MID', 'SENIOR', 'EXPERT']

export default function Users() {
  const me = useUser()
  const qc = useQueryClient()
  const users = useQuery({ queryKey: ['admin', 'users'], queryFn: () => api.get<User[]>('/admin/users') })
  const [filter, setFilter] = useState('')
  const [role, setRole] = useState<'' | Role>('')
  const [editing, setEditing] = useState<User | 'new' | null>(null)
  const [importing, setImporting] = useState(false)

  const toggleActive = useMutation({
    mutationFn: (u: User) => api.put<User>(`/admin/users/${u.id}`, { active: !u.active }),
    onSuccess: (u) => {
      qc.invalidateQueries({ queryKey: ['admin', 'users'] })
      toast.success(`${u.fullName} ${u.active ? 'reactivated' : 'deactivated'}`)
    },
    onError: (e) => toast.error((e as Error).message),
  })

  const rows = useMemo(() => (users.data ?? []).filter((u) =>
    (!role || u.role === role) &&
    (!filter || `${u.fullName} ${u.email} ${u.department ?? ''}`.toLowerCase().includes(filter.toLowerCase()))), [users.data, filter, role])

  return (
    <div>
      <PageHeader title="People" description="Everyone who can sign in, with their role, skills and current workload."
        actions={<>
          <Button icon={<FileUp className="size-4" />} onClick={() => setImporting(true)}>Import CSV</Button>
          <Button variant="primary" icon={<Plus className="size-4" />} onClick={() => setEditing('new')}>Add person</Button>
        </>} />

      <div className="mb-4 flex flex-wrap gap-2">
        <div className="relative min-w-[240px] flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-faint" />
          <Input aria-label="Search people" value={filter} onChange={(e) => setFilter(e.target.value)} placeholder="Search name, email or department" className="pl-9" />
        </div>
        <Select aria-label="Role" value={role} onChange={(e) => setRole(e.target.value as Role | '')} className="w-auto min-w-40">
          <option value="">Every role</option>
          {ROLES.map((r) => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}
        </Select>
      </div>

      <Panel bodyClassName="p-0">
        {users.isLoading ? <Skeleton className="m-5 h-64" /> : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[760px] text-[13px]">
              <thead className="border-b border-line text-left text-xs text-muted">
                <tr>
                  <th className="px-5 py-2.5 font-medium">Name</th>
                  <th className="py-2.5 font-medium">Role</th>
                  <th className="py-2.5 font-medium">Skills and load</th>
                  <th className="py-2.5 font-medium">Sign-in</th>
                  <th className="py-2.5 font-medium">Last sign-in</th>
                  <th className="px-5 py-2.5 text-right font-medium">Active</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {rows.map((u) => (
                  <tr key={u.id} className={cn('hover:bg-surface-2/50', !u.active && 'opacity-60')}>
                    <td className="px-5 py-2.5">
                      <button onClick={() => setEditing(u)} className="flex items-center gap-2.5 text-left cursor-pointer">
                        <Avatar name={u.fullName} size={30} />
                        <span>
                          <span className="block font-medium hover:text-accent-text">{u.fullName}</span>
                          <span className="block text-xs text-muted">{u.email}</span>
                        </span>
                      </button>
                    </td>
                    <td className="py-2.5">
                      <div>{ROLE_LABEL[u.role]}</div>
                      <div className="text-xs text-muted">{u.department}</div>
                    </td>
                    <td className="py-2.5">
                      {u.technician ? (
                        <div>
                          <div className="flex flex-wrap gap-1">
                            {u.technician.skills.map((s) => <span key={s} className="rounded bg-surface-2 px-1.5 py-px text-[11px] text-ink-2">{s.toLowerCase()}</span>)}
                          </div>
                          <div className="mt-1 text-xs text-muted">
                            {u.technician.activeIncidents ?? 0} of {u.technician.capacity} active, {u.technician.experienceLevel.toLowerCase()}
                            {!u.technician.available && ', off duty'}
                          </div>
                        </div>
                      ) : <span className="text-faint">—</span>}
                    </td>
                    <td className="py-2.5">
                      <div className="flex flex-wrap gap-1">
                        {u.googleLinked && <span className="rounded bg-surface-2 px-1.5 py-px text-[11px] text-ink-2">Google</span>}
                        {u.passwordEnabled && <span className="rounded bg-surface-2 px-1.5 py-px text-[11px] text-ink-2">Password</span>}
                        {u.mfaEnabled && <span className="rounded bg-ok-soft px-1.5 py-px text-[11px] font-medium text-ok">2FA</span>}
                        {u.lockedUntil && new Date(u.lockedUntil) > new Date() && <span className="rounded bg-p1-soft px-1.5 py-px text-[11px] font-medium text-p1">Locked</span>}
                      </div>
                    </td>
                    <td className="py-2.5 text-muted">{u.lastLoginAt ? ago(u.lastLoginAt) : 'Never'}</td>
                    <td className="px-5 py-2.5 text-right">
                      <Switch checked={u.active} onChange={() => toggleActive.mutate(u)} disabled={u.id === me.id} label={`${u.fullName} active`} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Panel>

      {editing && <UserDialog user={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
      {importing && <ImportDialog onClose={() => setImporting(false)} />}
    </div>
  )
}

const TEMPLATE = `email,name,role,department,skills
tara.nair@resolveiq.test,Tara Nair,TECHNICIAN,IT Operations,NETWORK;CLOUD
omar.khan@resolveiq.test,Omar Khan,EMPLOYEE,Physics,`

/** Pre-register people; they sign in with their company account and land in their role's workspace. */
function ImportDialog({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient()
  const [csv, setCsv] = useState('')
  const run = useMutation({
    mutationFn: () => api.post<{ line: number; email: string; outcome: string; detail: string }[]>('/admin/users/import', { csv }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'users'] }),
  })
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()} title="Import people from CSV" width={620}
      description="Columns: email, name, role, department, skills (separated by ;). Imported people sign in with their company Google account — no passwords are created."
      footer={run.data ? <Button variant="primary" onClick={onClose}>Done</Button>
        : <><Button variant="ghost" onClick={onClose}>Cancel</Button><Button variant="primary" loading={run.isPending} disabled={!csv.trim()} onClick={() => run.mutate()}>Import</Button></>}>
      {run.data ? (
        <ul className="space-y-1.5 text-[13px]">
          {run.data.map((r) => (
            <li key={r.line} className="flex gap-2">
              <span className={cn('w-16 shrink-0 text-xs font-semibold', r.outcome === 'CREATED' ? 'text-ok' : r.outcome === 'SKIPPED' ? 'text-muted' : 'text-p1')}>{r.outcome.toLowerCase()}</span>
              <span className="min-w-0 truncate">{r.email}</span>
              <span className="ml-auto truncate text-xs text-muted">{r.detail}</span>
            </li>
          ))}
        </ul>
      ) : (
        <div className="space-y-3">
          <Textarea rows={8} value={csv} onChange={(e) => setCsv(e.target.value)} className="font-mono text-[12.5px]" placeholder={TEMPLATE} aria-label="CSV" />
          <div className="flex flex-wrap items-center gap-2">
            <label className="inline-flex h-8 cursor-pointer items-center gap-1.5 rounded-md border border-line-strong px-2.5 text-[13px] font-medium hover:bg-surface-2">
              <FileUp className="size-3.5" /> Choose a .csv file
              <input type="file" accept=".csv,text/csv" className="sr-only" onChange={async (e) => { const f = e.target.files?.[0]; if (f) setCsv(await f.text()) }} />
            </label>
            <Button size="sm" variant="ghost" onClick={() => setCsv(TEMPLATE)}>Use the example</Button>
          </div>
          {run.error ? <ErrorNote error={run.error} /> : null}
        </div>
      )}
    </Dialog>
  )
}

/** Clearance and account security for an existing person. */
function AccessSection({ user, onChanged }: { user: User; onChanged: () => void }) {
  const me = useUser()
  const qc = useQueryClient()
  const [role, setRole] = useState<Role>(user.role)
  const [skills, setSkills] = useState<string[]>([])
  const becomingTechnical = (role === 'TECHNICIAN' || role === 'TEAM_LEAD') && !user.technician
  const done = (msg: string) => () => { qc.invalidateQueries({ queryKey: ['admin', 'users'] }); toast.success(msg); onChanged() }
  const fail = (e: unknown) => toast.error(e instanceof Error ? e.message : 'Failed')
  const changeRole = useMutation({
    mutationFn: () => api.post<User>(`/admin/users/${user.id}/role`, { role, skills: becomingTechnical ? skills : undefined }),
    onSuccess: done(`${user.fullName} is now ${ROLE_LABEL[role].toLowerCase()}; their sessions were ended`), onError: fail,
  })
  const unlock = useMutation({ mutationFn: () => api.post(`/admin/users/${user.id}/unlock`), onSuccess: done('Account unlocked'), onError: fail })
  const resetMfa = useMutation({ mutationFn: () => api.post(`/admin/users/${user.id}/reset-mfa`), onSuccess: done('Two-step verification reset'), onError: fail })
  const signOut = useMutation({ mutationFn: () => api.post(`/admin/users/${user.id}/revoke-sessions`), onSuccess: done('Signed out of every device'), onError: fail })
  const locked = user.lockedUntil && new Date(user.lockedUntil) > new Date()
  const self = user.id === me.id
  return (
    <section className="space-y-3 rounded-lg border border-line p-4">
      <h3 className="text-[13px] font-semibold">Access and security</h3>
      <div className="flex flex-wrap items-end gap-2">
        <Field label="Role (clearance)" htmlFor="u-role" className="min-w-48 flex-1"
          hint={self ? 'You cannot change your own role' : 'Changing it ends their sessions immediately'}>
          <Select id="u-role" value={role} disabled={self} onChange={(e) => setRole(e.target.value as Role)}>
            {ROLES.filter((r) => r !== 'MANAGER' || me.role === 'MANAGER' || user.role === 'MANAGER').map((r) => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}
          </Select>
        </Field>
        <Button disabled={self || role === user.role || (becomingTechnical && !skills.length)} loading={changeRole.isPending} onClick={() => changeRole.mutate()} className="mb-[22px]">Change role</Button>
      </div>
      {becomingTechnical && (
        <Field label="Skills for routing" htmlFor="u-skills" hint="At least one, e.g. NETWORK, DATABASE, AUTHENTICATION">
          <ChipsInput id="u-skills" value={skills} onChange={setSkills} placeholder="Type a skill and press Enter" />
        </Field>
      )}
      <div className="flex flex-wrap gap-2">
        {locked && <Button size="sm" icon={<LockOpen className="size-3.5" />} loading={unlock.isPending} onClick={() => unlock.mutate()}>Unlock account</Button>}
        {user.mfaEnabled && <Button size="sm" icon={<ShieldOff className="size-3.5" />} loading={resetMfa.isPending} onClick={() => resetMfa.mutate()}>Reset 2FA (lost phone)</Button>}
        {!self && <Button size="sm" icon={<LogOut className="size-3.5" />} loading={signOut.isPending} onClick={() => signOut.mutate()}>Sign out everywhere</Button>}
      </div>
      <p className="flex flex-wrap gap-x-3 text-xs text-muted">
        <span className="inline-flex items-center gap-1"><ShieldCheck className="size-3.5" />{user.mfaEnabled ? '2FA on' : '2FA off'}</span>
        <span className="inline-flex items-center gap-1"><KeyRound className="size-3.5" />{user.passwordEnabled ? 'Has a password' : 'Company account only'}</span>
        <span>{user.googleLinked ? 'Google linked' : 'Google not linked yet'}</span>
      </p>
    </section>
  )
}

function UserDialog({ user, onClose }: { user: User | null; onClose: () => void }) {
  const qc = useQueryClient()
  const categories = useQuery({ queryKey: ['categories'], queryFn: () => api.get<CategoryView[]>('/categories') })
  const users = useQuery({ queryKey: ['admin', 'users'], queryFn: () => api.get<User[]>('/admin/users') })
  const leads = (users.data ?? []).filter((u) => u.role === 'TEAM_LEAD' && u.id !== user?.id)

  const [form, setForm] = useState({
    role: (user?.role ?? 'EMPLOYEE') as Role,
    fullName: user?.fullName ?? '',
    email: user?.email ?? '',
    password: '',
    method: 'GOOGLE' as 'GOOGLE' | 'PASSWORD',
    phone: user?.phone ?? '',
    department: user?.department ?? '',
    skills: user?.technician?.skills ?? [] as string[],
    experienceLevel: user?.technician?.experienceLevel ?? 'MID',
    maxActiveIncidents: String(user?.technician?.capacity ?? 5),
    teamLeadId: user?.technician?.teamLeadId ? String(user.technician.teamLeadId) : '',
  })
  const techRole = form.role === 'TECHNICIAN' || form.role === 'TEAM_LEAD'

  const save = useMutation({
    mutationFn: () => user
      ? api.put(`/admin/users/${user.id}`, {
          fullName: form.fullName, phone: form.phone, department: form.department,
          skills: techRole ? form.skills : undefined, experienceLevel: techRole ? form.experienceLevel : undefined,
          maxActiveIncidents: techRole ? Number(form.maxActiveIncidents) : undefined,
          teamLeadId: techRole ? (form.teamLeadId ? Number(form.teamLeadId) : 0) : undefined,
        })
      : api.post('/admin/users', {
          ...form, password: form.method === 'PASSWORD' ? form.password : undefined, method: undefined,
          phone: form.phone || undefined, department: form.department || undefined,
          skills: techRole ? form.skills : undefined, experienceLevel: techRole ? form.experienceLevel : undefined,
          maxActiveIncidents: techRole ? Number(form.maxActiveIncidents) : undefined,
          teamLeadId: techRole && form.teamLeadId ? Number(form.teamLeadId) : undefined,
        }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'users'] })
      qc.invalidateQueries({ queryKey: ['technicians'] })
      toast.success(user ? 'Changes saved' : `${form.fullName} added`)
      onClose()
    },
  })
  const fe = save.error instanceof ApiError ? save.error.fieldErrors ?? {} : {}
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setForm({ ...form, [k]: e.target.value })
  const toggleSkill = (code: string) => setForm({ ...form, skills: form.skills.includes(code) ? form.skills.filter((s) => s !== code) : [...form.skills, code] })

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()} title={user ? user.fullName : 'Add a person'} width={560}
      description={user ? `${ROLE_LABEL[user.role]}, ${user.email}` : 'The right kind of account is created from the role you choose.'}
      footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button variant="primary" loading={save.isPending} onClick={() => save.mutate()}>{user ? 'Save changes' : 'Add person'}</Button></>}>
      <div className="space-y-4">
        {!user && (
          <Field label="Role">
            <Select value={form.role} onChange={set('role')}>{ROLES.map((r) => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}</Select>
          </Field>
        )}
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Full name" error={fe.fullName}><Input value={form.fullName} onChange={set('fullName')} /></Field>
          <Field label="Department"><Input value={form.department} onChange={set('department')} /></Field>
        </div>
        {!user && (
          <>
            <Field label="Work email" error={fe.email}><Input type="email" value={form.email} onChange={set('email')} /></Field>
            <Field label="How they sign in">
              <Segmented name="Sign-in method" value={form.method} onChange={(v) => setForm({ ...form, method: v })}
                options={[{ value: 'GOOGLE', label: 'Company account (Google)' }, { value: 'PASSWORD', label: 'Temporary password' }]} />
            </Field>
            {form.method === 'PASSWORD' ? (
              <Field label="Temporary password" error={fe.password} hint="8+ characters, letter and digit; ask them to change it"><Input type="text" value={form.password} onChange={set('password')} /></Field>
            ) : (
              <p className="-mt-2 text-xs text-muted">They sign in with Google using this address and land straight in the {ROLE_LABEL[form.role].toLowerCase()} workspace. No password is created.</p>
            )}
          </>
        )}
        <Field label="Phone" hint="Used for SMS alerts on critical incidents" error={fe.phone}><Input value={form.phone} onChange={set('phone')} /></Field>
        {techRole && (
          <>
            <Field label="Skills" hint="Incidents in these categories are routed to this person">
              <div className="flex flex-wrap gap-1.5">
                {categories.data?.map((c) => (
                  <button key={c.code} type="button" onClick={() => toggleSkill(c.code)} aria-pressed={form.skills.includes(c.code)}
                    className={cn('rounded-md border px-2 py-1 text-xs transition-colors cursor-pointer',
                      form.skills.includes(c.code) ? 'border-accent bg-accent-soft font-medium text-accent-text' : 'border-line text-ink-2 hover:bg-surface-2')}>
                    {c.name}
                  </button>
                ))}
              </div>
            </Field>
            <div className="grid gap-4 sm:grid-cols-3">
              <Field label="Experience">
                <Select value={form.experienceLevel} onChange={set('experienceLevel')}>{LEVELS.map((l) => <option key={l} value={l}>{l.charAt(0) + l.slice(1).toLowerCase()}</option>)}</Select>
              </Field>
              <Field label="Capacity" hint="Max active incidents"><Input type="number" min={1} max={50} value={form.maxActiveIncidents} onChange={set('maxActiveIncidents')} /></Field>
              <Field label="Reports to">
                <Select value={form.teamLeadId} onChange={set('teamLeadId')}>
                  <option value="">Nobody</option>
                  {leads.map((l) => <option key={l.id} value={l.id}>{l.fullName}</option>)}
                </Select>
              </Field>
            </div>
          </>
        )}
        {user && <AccessSection user={user} onChanged={onClose} />}
        {save.error && !Object.keys(fe).length ? <ErrorNote error={save.error} /> : null}
      </div>
    </Dialog>
  )
}
