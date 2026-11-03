import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { MailCheck } from 'lucide-react'
import { api } from '@/lib/api'
import { Button, ErrorNote, Field, Input } from '@/components/ui'
import { AuthLayout } from '@/components/AuthLayout'

export default function ForgotPassword() {
  const [email, setEmail] = useState('')
  const [busy, setBusy] = useState(false)
  const [sent, setSent] = useState(false)
  const [error, setError] = useState<unknown>(null)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await api.post('/auth/password/forgot', { email })
      setSent(true)
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  if (sent) {
    return (
      <AuthLayout icon={<MailCheck className="size-5" />} title="Check your e-mail"
        description={<>If <span className="font-medium text-ink-2">{email}</span> has an account, a link to choose a new password is on its way. It works once, for 30 minutes.</>}>
        <Link to="/login" className="text-[13px] font-medium text-accent-text hover:underline">Back to sign in</Link>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout title="Reset your password" description="Enter your work e-mail and we'll send you a link to choose a new password.">
      <form onSubmit={submit} className="space-y-4">
        <Field label="Work email" htmlFor="f-email">
          <Input id="f-email" type="email" required autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} />
        </Field>
        {error ? <ErrorNote error={error} /> : null}
        <Button type="submit" variant="primary" className="w-full" loading={busy}>Send reset link</Button>
      </form>
      <p className="mt-6 text-[13px] text-muted">
        Remembered it? <Link to="/login" className="font-medium text-accent-text hover:underline">Sign in</Link>
      </p>
    </AuthLayout>
  )
}
