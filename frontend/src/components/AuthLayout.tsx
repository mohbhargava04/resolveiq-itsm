import type { ReactNode } from 'react'
import { Logo, ThemeMenu } from './shell'

/** Single-column layout for the account pages around sign-in (register, reset password, welcome). */
export function AuthLayout({ title, description, icon, children, wide }: {
  title: string; description?: ReactNode; icon?: ReactNode; children: ReactNode; wide?: boolean
}) {
  return (
    <div className="flex min-h-dvh flex-col px-6 py-8 sm:px-12">
      <header className="flex items-center justify-between">
        <Logo />
        <ThemeMenu />
      </header>
      <main className={`mx-auto flex w-full ${wide ? 'max-w-lg' : 'max-w-sm'} flex-1 flex-col justify-center py-12`}>
        {icon && <span className="mb-4 inline-flex size-10 items-center justify-center rounded-full bg-accent-soft text-accent-text">{icon}</span>}
        <h1 className="text-[28px] font-semibold leading-tight tracking-tight">{title}</h1>
        {description && <p className="mt-2 text-sm text-muted">{description}</p>}
        <div className="mt-8">{children}</div>
      </main>
    </div>
  )
}
