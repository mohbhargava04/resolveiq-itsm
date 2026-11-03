import { useState, type FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { BadgeCheck } from 'lucide-react'
import { api, ApiError } from '@/lib/api'
import { useAuth } from '@/lib/auth'
import { ROLE_LABEL } from '@/lib/format'
import type { User } from '@/lib/types'
import { Avatar, Button, ChipsInput, ErrorNote, Field, Input } from '@/components/ui'
import { AuthLayout } from '@/components/AuthLayout'

/**
 * First sign-in with a company account. Google already told us the name, e-mail and photo; we only
 * ask for what it does not know. The access level is shown but cannot be changed here.
 */
export default function Welcome() {
  const { user, setUser } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({
    fullName: user?.fullName ?? '', phone: user?.phone ?? '', department: user?.department ?? '', location: user?.location ?? '',
  })
  const [skills, setSkills] = useState<string[]>(user?.technician?.skills ?? [])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  if (!user) return <Navigate to="/login" replace />
  if (user.profileCompleted) return <Navigate to="/" replace />

  const fieldErrors = error instanceof ApiError ? error.fieldErrors ?? {} : {}
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [k]: e.target.value })

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      setUser(await api.post<User>('/auth/profile/complete', { ...form, skills: user.technician ? skills : undefined }))
      navigate('/', { replace: true })
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthLayout wide title={`Welcome, ${user.fullName.split(' ')[0]}`}
      description="Confirm a few details to finish setting up your account. It takes less than a minute.">
      <div className="mb-6 flex items-center gap-4 rounded-xl border border-line bg-surface p-4">
        <Avatar name={user.fullName} src={user.avatarUrl} size={48} />
        <div className="min-w-0 flex-1">
          <p className="truncate font-medium">{user.email}</p>
          <p className="mt-0.5 flex items-center gap-1.5 text-[13px] text-muted">
            <BadgeCheck className="size-4 text-accent-text" aria-hidden />
            Access level: <span className="font-medium text-ink-2">{ROLE_LABEL[user.role]}</span>
          </p>
        </div>
      </div>
      <p className="-mt-3 mb-6 text-xs text-muted">Your access level is set by your IT administrator. Ask them if it should be different.</p>

      <form onSubmit={submit} className="space-y-4">
        <Field label="Full name" htmlFor="w-name" error={fieldErrors.fullName}>
          <Input id="w-name" required value={form.fullName} onChange={set('fullName')} autoComplete="name" />
        </Field>
        <Field label="Mobile number" htmlFor="w-phone" hint="Used for urgent alerts by SMS. Optional." error={fieldErrors.phone}>
          <Input id="w-phone" value={form.phone} onChange={set('phone')} autoComplete="tel" placeholder="+91 98765 43210" />
        </Field>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Department" htmlFor="w-dept">
            <Input id="w-dept" value={form.department} onChange={set('department')} placeholder="e.g. Finance" />
          </Field>
          <Field label="Location" htmlFor="w-loc" hint="Building or campus">
            <Input id="w-loc" value={form.location} onChange={set('location')} placeholder="e.g. Library block" />
          </Field>
        </div>
        {user.technician && (
          <Field label="Your skills" htmlFor="w-skills" hint="Incidents are routed to you by these. Press Enter after each.">
            <ChipsInput id="w-skills" value={skills} onChange={setSkills} placeholder="NETWORK, DATABASE…" />
          </Field>
        )}
        {error && !Object.keys(fieldErrors).length ? <ErrorNote error={error} /> : null}
        <Button type="submit" variant="primary" className="w-full" loading={busy}>Continue to ResolveIQ</Button>
      </form>
    </AuthLayout>
  )
}
