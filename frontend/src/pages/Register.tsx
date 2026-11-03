import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useAuth } from '@/lib/auth'
import { api, ApiError } from '@/lib/api'
import type { AuthOptions } from '@/lib/types'
import { Button, ErrorNote, Field, Input } from '@/components/ui'
import { AuthLayout } from '@/components/AuthLayout'

export default function Register() {
  const { user, register } = useAuth()
  const navigate = useNavigate()
  const options = useQuery({ queryKey: ['auth-options'], queryFn: () => api.get<AuthOptions>('/auth/options') })
  const [form, setForm] = useState({ fullName: '', email: '', password: '', phone: '', department: '' })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  if (user) return <Navigate to="/" replace />

  const domains = options.data?.allowedDomains ?? []
  const fieldErrors = error instanceof ApiError ? error.fieldErrors ?? {} : {}
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [k]: e.target.value })

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await register({ ...form, phone: form.phone || undefined, department: form.department || undefined })
      navigate('/', { replace: true })
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  if (options.data && !options.data.selfRegistration) {
    return (
      <AuthLayout title="Accounts are created by IT" description="Your IT administrator adds people to ResolveIQ. Sign in with your company account once you've been added.">
        <Link to="/login" className="text-[13px] font-medium text-accent-text hover:underline">Back to sign in</Link>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout title="Create your account" description="Employees can register themselves. IT staff accounts are created by an administrator.">
      <form onSubmit={submit} className="space-y-4">
        <Field label="Full name" htmlFor="r-name" error={fieldErrors.fullName}>
          <Input id="r-name" required value={form.fullName} onChange={set('fullName')} autoComplete="name" />
        </Field>
        <Field label="Work email" htmlFor="r-email" error={fieldErrors.email}
          hint={domains.length ? `Company addresses only: ${domains.map((d) => '@' + d).join(', ')}` : undefined}>
          <Input id="r-email" type="email" required value={form.email} onChange={set('email')} autoComplete="email" />
        </Field>
        <Field label="Password" htmlFor="r-pass" hint="At least 8 characters with a letter and a digit. Common passwords are refused." error={fieldErrors.password}>
          <Input id="r-pass" type="password" required minLength={8} value={form.password} onChange={set('password')} autoComplete="new-password" />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Phone" htmlFor="r-phone" error={fieldErrors.phone}>
            <Input id="r-phone" value={form.phone} onChange={set('phone')} autoComplete="tel" placeholder="Optional" />
          </Field>
          <Field label="Department" htmlFor="r-dept">
            <Input id="r-dept" value={form.department} onChange={set('department')} placeholder="Optional" />
          </Field>
        </div>
        {error && !Object.keys(fieldErrors).length ? <ErrorNote error={error} /> : null}
        <Button type="submit" variant="primary" className="w-full" loading={busy}>Create account</Button>
      </form>
      <p className="mt-6 text-[13px] text-muted">
        Already registered? <Link to="/login" className="font-medium text-accent-text hover:underline">Sign in</Link>
      </p>
    </AuthLayout>
  )
}
