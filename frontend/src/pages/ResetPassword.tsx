import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { CheckCircle2 } from 'lucide-react'
import { api } from '@/lib/api'
import { Button, ErrorNote, Field, Input } from '@/components/ui'
import { AuthLayout } from '@/components/AuthLayout'

export default function ResetPassword() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [busy, setBusy] = useState(false)
  const [done, setDone] = useState(false)
  const [error, setError] = useState<unknown>(null)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (password !== confirm) {
      setError(new Error('The two passwords do not match.'))
      return
    }
    setBusy(true)
    setError(null)
    try {
      await api.post('/auth/password/reset', { token, password })
      setDone(true)
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  if (done) {
    return (
      <AuthLayout icon={<CheckCircle2 className="size-5" />} title="Password changed"
        description="You've been signed out everywhere else for safety. Sign in with your new password.">
        <Link to="/login"><Button variant="primary" className="w-full">Sign in</Button></Link>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout title="Choose a new password" description="At least 8 characters with a letter and a digit. Common passwords are refused.">
      {!token ? (
        <ErrorNote error={new Error('This link is incomplete. Open the link from the e-mail again, or ask for a new one.')} />
      ) : (
        <form onSubmit={submit} className="space-y-4">
          <Field label="New password" htmlFor="n-pass">
            <Input id="n-pass" type="password" required minLength={8} autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} />
          </Field>
          <Field label="Confirm new password" htmlFor="n-pass2">
            <Input id="n-pass2" type="password" required minLength={8} autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} />
          </Field>
          {error ? <ErrorNote error={error} /> : null}
          <Button type="submit" variant="primary" className="w-full" loading={busy}>Change password</Button>
        </form>
      )}
      <p className="mt-6 text-[13px] text-muted">
        <Link to="/forgot-password" className="font-medium text-accent-text hover:underline">Ask for a new link</Link>
      </p>
    </AuthLayout>
  )
}
