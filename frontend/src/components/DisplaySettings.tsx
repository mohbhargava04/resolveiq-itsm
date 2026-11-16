import { useEffect, useRef } from 'react'
import { Check, Contrast, Monitor, Moon, MoonStar, Sun, Sunrise } from 'lucide-react'
import { api } from '@/lib/api'
import { useAuth } from '@/lib/auth'
import { cn } from '@/lib/format'
import {
  DEFAULT_DISPLAY, normaliseDisplay, sameDisplay, TEXT_SIZES, THEMES,
  type DisplayPreferences, type ThemeChoice,
} from '@/lib/display'
import { useDisplay } from '@/lib/theme'
import type { User } from '@/lib/types'
import { Button, Segmented, Switch } from './ui'
import { PriorityBadge, SlaFuse, StatusBadge } from './incident-bits'

export const THEME_ICON: Record<ThemeChoice, typeof Sun> = {
  SYSTEM: Monitor, LIGHT: Sun, WARM: Sunrise, DIM: MoonStar, DARK: Moon, HIGH_CONTRAST: Contrast,
}

/** Two swatches per theme (page and text colour) so the choice can be seen before it is made. */
const SWATCH: Record<ThemeChoice, [string, string]> = {
  SYSTEM: ['linear-gradient(135deg, #ffffff 50%, #1a1e24 50%)', '#0f6b6f'],
  LIGHT: ['#ffffff', '#15181e'],
  WARM: ['#fbf7ef', '#2a2520'],
  DIM: ['#2b2f36', '#e1e4e8'],
  DARK: ['#1a1e24', '#e8eaed'],
  HIGH_CONTRAST: ['#ffffff', '#000000'],
}

/**
 * The "Display & accessibility" settings. Every control applies at once (the page itself is the
 * live preview, and a sample incident row shows text, badges and a link together). Used in the
 * profile page and in the dialog opened from the theme menu, including before signing in.
 */
export function DisplaySettings() {
  const { display, update } = useDisplay()
  const { user } = useAuth()

  return (
    <div className="space-y-7">
      <fieldset>
        <legend className="text-[0.8125rem] font-medium text-ink-2">Theme</legend>
        <div role="radiogroup" aria-label="Theme" className="mt-2 grid grid-cols-2 gap-2 sm:grid-cols-3">
          {THEMES.map((t) => {
            const Icon = THEME_ICON[t.value]
            const on = display.theme === t.value
            return (
              <button key={t.value} type="button" role="radio" aria-checked={on} onClick={() => update({ theme: t.value })}
                className={cn('flex items-start gap-2.5 rounded-lg border p-2.5 text-left transition-colors cursor-pointer',
                  on ? 'border-accent bg-accent-soft ring-1 ring-accent' : 'border-line-strong hover:bg-surface-2')}>
                <span aria-hidden className="mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-md border border-line-strong"
                  style={{ background: SWATCH[t.value][0], color: SWATCH[t.value][1] }}>
                  <Icon className="size-4" />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="flex items-center gap-1 text-[0.8125rem] font-medium text-ink">
                    {t.label}{on && <Check className="size-3.5 text-accent-text" aria-hidden />}
                  </span>
                  <span className="block text-xs text-muted">{t.hint}</span>
                </span>
              </button>
            )
          })}
        </div>
      </fieldset>

      <div>
        <p id="ds-size" className="text-[0.8125rem] font-medium text-ink-2">Text size</p>
        <p className="mb-2 text-xs text-muted">Scales all text and spacing together, like zooming the page.</p>
        <Segmented name="Text size" value={display.textSize} onChange={(textSize) => update({ textSize })} options={TEXT_SIZES} />
      </div>

      <div className="divide-y divide-line rounded-lg border border-line">
        <Toggle label="Reduce motion" hint="Turns off animation and transitions, even if this device allows them."
          checked={display.reduceMotion} onChange={(reduceMotion) => update({ reduceMotion })} />
        <Toggle label="Easier reading" hint="More space between lines, letters and words."
          checked={display.easierReading} onChange={(easierReading) => update({ easierReading })} />
        <Toggle label="Underline links" hint="Links are always underlined, so they never rely on colour."
          checked={display.underlineLinks} onChange={(underlineLinks) => update({ underlineLinks })} />
      </div>

      <div>
        <p className="mb-2 text-[0.8125rem] font-medium text-ink-2">Preview</p>
        <div className="rounded-lg border border-line bg-bg p-4" data-testid="display-preview">
          <div className="rounded-lg border border-line bg-surface p-4">
            <div className="flex flex-wrap items-center gap-2">
              <PriorityBadge priority="P1" showLabel />
              <span className="font-medium text-ink">Payments are failing at checkout</span>
            </div>
            <p className="mt-1.5 text-[0.8125rem] text-muted">
              INC-1024 · Payment Gateway · reported 12 minutes ago. Status and SLA are always shown with words
              and icons, never colour alone.
            </p>
            <a href="#display-preview" onClick={(e) => e.preventDefault()} className="mt-1 inline-block text-[0.8125rem] text-accent-text hover:underline">This is a link</a>
            <div className="mt-3 grid items-center gap-3 sm:grid-cols-[auto_1fr_auto]">
              <StatusBadge status="IN_PROGRESS" />
              <SlaFuse compact status="AT_RISK" elapsedPercent={82} minutesRemaining={9} />
              <Button size="sm" variant="primary" type="button">Resolve</Button>
            </div>
          </div>
        </div>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-xs text-muted" role="status">
          {user ? 'Saved to your account, so these settings follow you to any device.' : 'Saved on this device. Sign in to keep them on your account.'}
        </p>
        <Button size="sm" variant="ghost" type="button" disabled={sameDisplay(display, DEFAULT_DISPLAY)} onClick={() => update(DEFAULT_DISPLAY)}>
          Reset to defaults
        </Button>
      </div>
    </div>
  )
}

function Toggle({ label, hint, checked, onChange }: { label: string; hint: string; checked: boolean; onChange: (v: boolean) => void }) {
  return (
    <div className="flex items-center gap-4 px-4 py-3">
      <div className="min-w-0 flex-1">
        <p className="text-[0.84375rem] font-medium text-ink">{label}</p>
        <p className="text-xs text-muted">{hint}</p>
      </div>
      <Switch checked={checked} onChange={onChange} label={label} />
    </div>
  )
}

/**
 * Keeps the signed-in person's account and this device in step. Renders nothing.
 *  - On sign-in the account's settings win, unless the account has never been customised and this
 *    device has (then the device's settings are adopted and saved to the account).
 *  - Afterwards every change is saved to the account (debounced, so dragging through options
 *    sends one request).
 */
export function DisplayAccountSync() {
  const { user, setUser } = useAuth()
  const { display, update } = useDisplay()
  const syncedUser = useRef<number | null>(null)
  const lastSaved = useRef<DisplayPreferences | null>(null)

  useEffect(() => {
    if (!user) { syncedUser.current = null; lastSaved.current = null; return }
    if (syncedUser.current === user.id) return
    syncedUser.current = user.id
    const account = normaliseDisplay(user.display)
    if (sameDisplay(account, DEFAULT_DISPLAY) && !sameDisplay(display, DEFAULT_DISPLAY)) {
      lastSaved.current = account // differs from `display`, so the effect below saves it
    } else {
      lastSaved.current = account
      if (!sameDisplay(account, display)) update(account)
    }
  }, [user, display, update])

  useEffect(() => {
    if (!user || syncedUser.current !== user.id || !lastSaved.current || sameDisplay(lastSaved.current, display)) return
    const timer = setTimeout(() => {
      lastSaved.current = display
      api.put<User>('/auth/preferences', display).then(setUser).catch(() => { lastSaved.current = null })
    }, 500)
    return () => clearTimeout(timer)
  }, [user, display, setUser])

  return null
}
