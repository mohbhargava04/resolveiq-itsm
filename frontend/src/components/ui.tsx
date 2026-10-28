import { forwardRef, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react'
import * as RDialog from '@radix-ui/react-dialog'
import { Loader2, X } from 'lucide-react'
import { cn, initials } from '@/lib/format'

// ---------------------------------------------------------------------------------------------
// Button
// ---------------------------------------------------------------------------------------------

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'subtle'
type Size = 'sm' | 'md'

const VARIANTS: Record<Variant, string> = {
  primary: 'bg-accent text-accent-ink hover:bg-accent-hover shadow-[inset_0_1px_0_rgb(255_255_255/0.12)]',
  secondary: 'bg-surface text-ink border border-line-strong hover:bg-surface-2',
  ghost: 'text-ink-2 hover:bg-surface-2 hover:text-ink',
  subtle: 'bg-surface-2 text-ink hover:bg-surface-3',
  danger: 'bg-p1 text-white hover:opacity-90',
}

const SIZES: Record<Size, string> = {
  sm: 'h-8 px-2.5 text-[13px] gap-1.5 rounded-md',
  md: 'h-9 px-3.5 text-sm gap-2 rounded-lg',
}

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant
  size?: Size
  loading?: boolean
  icon?: ReactNode
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = 'secondary', size = 'md', loading, icon, className, children, disabled, ...rest }, ref,
) {
  return (
    <button
      ref={ref}
      disabled={disabled || loading}
      className={cn(
        'inline-flex shrink-0 items-center justify-center font-medium whitespace-nowrap transition-colors',
        'disabled:pointer-events-none disabled:opacity-50 cursor-pointer',
        VARIANTS[variant], SIZES[size], className,
      )}
      {...rest}
    >
      {loading ? <Loader2 className="size-4 animate-spin" aria-hidden /> : icon}
      {children}
    </button>
  )
})

export function IconButton({ label, className, children, ...rest }: ButtonHTMLAttributes<HTMLButtonElement> & { label: string }) {
  return (
    <button
      aria-label={label}
      title={label}
      className={cn('inline-flex size-8 items-center justify-center rounded-md text-muted hover:bg-surface-2 hover:text-ink transition-colors cursor-pointer', className)}
      {...rest}
    >
      {children}
    </button>
  )
}

// ---------------------------------------------------------------------------------------------
// Form controls
// ---------------------------------------------------------------------------------------------

const fieldBase =
  'w-full rounded-lg border border-line-strong bg-surface px-3 text-sm text-ink placeholder:text-faint ' +
  'transition-colors focus:outline-none focus:border-accent focus:ring-3 focus:ring-accent/15 disabled:opacity-60'

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(function Input({ className, ...rest }, ref) {
  return <input ref={ref} className={cn(fieldBase, 'h-9', className)} {...rest} />
})

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement>>(function Textarea({ className, ...rest }, ref) {
  return <textarea ref={ref} className={cn(fieldBase, 'py-2 leading-relaxed resize-y min-h-20', className)} {...rest} />
})

export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement>>(function Select({ className, children, ...rest }, ref) {
  return (
    <select
      ref={ref}
      className={cn(fieldBase, 'h-9 appearance-none pr-8 bg-[length:16px] bg-[right_0.6rem_center] bg-no-repeat cursor-pointer', className)}
      style={{ backgroundImage: "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%238a919c' stroke-width='2'%3E%3Cpath d='m6 9 6 6 6-6'/%3E%3C/svg%3E\")" }}
      {...rest}
    >
      {children}
    </select>
  )
})

export function Field({ label, hint, error, children, className, htmlFor }: {
  label: string; hint?: ReactNode; error?: string; children: ReactNode; className?: string; htmlFor?: string
}) {
  return (
    <div className={cn('flex flex-col gap-1.5', className)}>
      <label htmlFor={htmlFor} className="text-[13px] font-medium text-ink-2">{label}</label>
      {children}
      {error ? <p className="text-xs text-p1">{error}</p> : hint ? <p className="text-xs text-muted">{hint}</p> : null}
    </div>
  )
}

/** A row of mutually exclusive options; used for impact/urgency where every option deserves a label. */
export function Segmented<T extends string>({ value, onChange, options, name }: {
  value: T; onChange: (v: T) => void; options: { value: T; label: string; hint?: string }[]; name: string
}) {
  return (
    <div role="radiogroup" aria-label={name} className="grid grid-flow-col auto-cols-fr gap-1 rounded-lg bg-surface-2 p-1">
      {options.map((o) => (
        <button
          key={o.value}
          type="button"
          role="radio"
          aria-checked={value === o.value}
          title={o.hint}
          onClick={() => onChange(o.value)}
          className={cn(
            'h-8 rounded-md text-[13px] font-medium transition-all cursor-pointer',
            value === o.value ? 'bg-surface text-ink shadow-sm ring-1 ring-line' : 'text-muted hover:text-ink',
          )}
        >
          {o.label}
        </button>
      ))}
    </div>
  )
}

export function Switch({ checked, onChange, label, disabled }: { checked: boolean; onChange: (v: boolean) => void; label: string; disabled?: boolean }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={cn(
        'relative inline-flex h-5 w-9 shrink-0 items-center rounded-full transition-colors cursor-pointer disabled:opacity-50',
        checked ? 'bg-accent' : 'bg-line-strong',
      )}
    >
      <span className={cn('inline-block size-4 rounded-full bg-white shadow transition-transform', checked ? 'translate-x-[18px]' : 'translate-x-0.5')} />
    </button>
  )
}

// ---------------------------------------------------------------------------------------------
// Display
// ---------------------------------------------------------------------------------------------

export function Avatar({ name, size = 28, className }: { name?: string; size?: number; className?: string }) {
  // Deterministic, muted hue per person so avatars are distinguishable but never loud.
  const hue = [...(name ?? '?')].reduce((a, c) => a + c.charCodeAt(0), 0) % 360
  return (
    <span
      aria-hidden
      className={cn('inline-flex shrink-0 items-center justify-center rounded-full font-semibold', className)}
      style={{
        width: size, height: size, fontSize: size * 0.38,
        background: `hsl(${hue} 30% var(--avatar-l, 90%))`, color: `hsl(${hue} 35% var(--avatar-ink-l, 28%))`,
      }}
    >
      {initials(name)}
    </span>
  )
}

export function Spinner({ className }: { className?: string }) {
  return <Loader2 className={cn('size-4 animate-spin text-muted', className)} aria-label="Loading" />
}

export function Skeleton({ className }: { className?: string }) {
  return <div className={cn('animate-pulse rounded-md bg-surface-2', className)} />
}

export function Kbd({ children }: { children: ReactNode }) {
  return <kbd className="rounded border border-line bg-surface-2 px-1.5 py-px text-[11px] font-medium text-muted">{children}</kbd>
}

export function Panel({ title, action, children, className, bodyClassName, description }: {
  title?: ReactNode; action?: ReactNode; children: ReactNode; className?: string; bodyClassName?: string; description?: ReactNode
}) {
  return (
    <section className={cn('rounded-xl border border-line bg-surface', className)}>
      {(title || action) && (
        <header className="flex items-start justify-between gap-3 px-5 pt-4 pb-3">
          <div className="min-w-0">
            {title && <h2 className="text-[15px] font-semibold">{title}</h2>}
            {description && <p className="mt-0.5 text-[13px] text-muted">{description}</p>}
          </div>
          {action}
        </header>
      )}
      <div className={cn(title || action ? 'px-5 pb-5' : 'p-5', bodyClassName)}>{children}</div>
    </section>
  )
}

export function EmptyState({ icon, title, children, action }: { icon?: ReactNode; title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center px-6 py-12 text-center">
      {icon && <div className="mb-3 text-faint">{icon}</div>}
      <p className="font-medium text-ink">{title}</p>
      {children && <p className="mt-1 max-w-sm text-[13px] text-muted">{children}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}

export function PageHeader({ title, description, actions }: { title: ReactNode; description?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div className="min-w-0">
        <h1 className="text-[22px] font-semibold leading-tight text-balance">{title}</h1>
        {description && <p className="mt-1 text-sm text-muted">{description}</p>}
      </div>
      {actions && <div className="flex items-center gap-2">{actions}</div>}
    </div>
  )
}

export function ErrorNote({ error }: { error: unknown }) {
  if (!error) return null
  const message = error instanceof Error ? error.message : 'Something went wrong'
  return <div role="alert" className="rounded-lg border border-p1/30 bg-p1-soft px-3 py-2 text-[13px] text-p1">{message}</div>
}

// ---------------------------------------------------------------------------------------------
// Dialog
// ---------------------------------------------------------------------------------------------

export function Dialog({ open, onOpenChange, title, description, children, footer, width = 480 }: {
  open: boolean; onOpenChange: (o: boolean) => void; title: string; description?: ReactNode
  children: ReactNode; footer?: ReactNode; width?: number
}) {
  return (
    <RDialog.Root open={open} onOpenChange={onOpenChange}>
      <RDialog.Portal>
        <RDialog.Overlay className="fixed inset-0 z-40 bg-black/30 backdrop-blur-[2px] data-[state=open]:animate-[fade-in_0.15s]" />
        <RDialog.Content
          className="fixed left-1/2 top-[12vh] z-50 w-[calc(100vw-32px)] -translate-x-1/2 rounded-xl border border-line bg-surface shadow-pop animate-slide-in focus:outline-none"
          style={{ maxWidth: width }}
        >
          <div className="flex items-start justify-between gap-4 border-b border-line px-5 py-4">
            <div>
              <RDialog.Title className="text-[15px] font-semibold">{title}</RDialog.Title>
              {description && <RDialog.Description className="mt-0.5 text-[13px] text-muted">{description}</RDialog.Description>}
            </div>
            <RDialog.Close asChild>
              <IconButton label="Close"><X className="size-4" /></IconButton>
            </RDialog.Close>
          </div>
          <div className="max-h-[60vh] overflow-y-auto px-5 py-4">{children}</div>
          {footer && <div className="flex justify-end gap-2 border-t border-line px-5 py-3">{footer}</div>}
        </RDialog.Content>
      </RDialog.Portal>
    </RDialog.Root>
  )
}
