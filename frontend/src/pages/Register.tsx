import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '@/lib/auth'
import { ApiError } from '@/lib/api'
import { Button, ErrorNote, Field, Input } from '@/components/ui'
import { Logo } from '@/components/shell'

export default function Register() {
  const { user, register } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ fullName: '', email: '', password: '', phone: '', department: '' })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  if (user) return <Navigate to="/" replace />

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

  return (
    <div className="flex min-h-dvh flex-col px-6 py-8 sm:px-12">
      <Logo />
      <div className="mx-auto flex w-full max-w-sm flex-1 flex-col justify-center py-12">
        <h1 className="text-[28px] font-semibold leading-tight tracking-tight">Create your account</h1>
        <p className="mt-2 text-sm text-muted">Employees can register themselves. IT staff accounts are created by an administrator.</p>
        <form onSubmit={submit} className="mt-8 space-y-4">
          <Field label="Full name" error={fieldErrors.fullName}>
            <Input required value={form.fullName} onChange={set('fullName')} autoComplete="name" />
          </Field>
          <Field label="Work email" error={fieldErrors.email}>
            <Input type="email" required value={form.email} onChange={set('email')} autoComplete="email" />
          </Field>
          <Field label="Password" hint="At least 8 characters, with a letter and a digit" error={fieldErrors.password}>
            <Input type="password" required minLength={8} value={form.password} onChange={set('password')} autoComplete="new-password" />
          </Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Phone" error={fieldErrors.phone}>
              <Input value={form.phone} onChange={set('phone')} autoComplete="tel" placeholder="Optional" />
            </Field>
            <Field label="Department">
              <Input value={form.department} onChange={set('department')} placeholder="Optional" />
            </Field>
          </div>
          {error && !Object.keys(fieldErrors).length ? <ErrorNote error={error} /> : null}
          <Button type="submit" variant="primary" className="w-full" loading={busy}>Create account</Button>
        </form>
        <p className="mt-6 text-[13px] text-muted">
          Already registered? <Link to="/login" className="font-medium text-accent-text hover:underline">Sign in</Link>
        </p>
      </div>
    </div>
  )
}
