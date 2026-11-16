import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import {
  applyDisplay, isDarkTheme, readStoredDisplay, resolveTheme, storeDisplay,
  type DisplayPreferences, type ThemeChoice, type ThemeName,
} from './display'

interface DisplayState {
  /** Everything the person chose: theme, text size and reading aids. */
  display: DisplayPreferences
  /** The palette on screen right now. */
  themeName: ThemeName
  /** Whether that palette is a dark one (for third-party widgets that only know light/dark). */
  resolved: 'light' | 'dark'
  /** Change one or more settings; applied to the page at once and remembered on this device. */
  update: (change: Partial<DisplayPreferences>) => void
  setTheme: (theme: ThemeChoice) => void
}

const DisplayContext = createContext<DisplayState | null>(null)

/**
 * Holds the display settings and keeps <html> in step with them. Saving them to the signed-in
 * person's account is done by DisplayAccountSync, which sits inside the auth provider.
 */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const [display, setDisplay] = useState<DisplayPreferences>(readStoredDisplay)
  const [systemDark, setSystemDark] = useState(() => matchMedia('(prefers-color-scheme: dark)').matches)

  useEffect(() => {
    const mq = matchMedia('(prefers-color-scheme: dark)')
    const onChange = (e: MediaQueryListEvent) => setSystemDark(e.matches)
    mq.addEventListener('change', onChange)
    return () => mq.removeEventListener('change', onChange)
  }, [])

  useEffect(() => { applyDisplay(display, systemDark) }, [display, systemDark])

  const update = useCallback((change: Partial<DisplayPreferences>) => {
    setDisplay((current) => {
      const next = { ...current, ...change }
      storeDisplay(next)
      return next
    })
  }, [])

  const value = useMemo<DisplayState>(() => {
    const themeName = resolveTheme(display.theme, systemDark)
    return {
      display, themeName, resolved: isDarkTheme(themeName) ? 'dark' : 'light',
      update, setTheme: (theme) => update({ theme }),
    }
  }, [display, systemDark, update])

  return <DisplayContext.Provider value={value}>{children}</DisplayContext.Provider>
}

export function useDisplay() {
  const ctx = useContext(DisplayContext)
  if (!ctx) throw new Error('useDisplay must be used inside ThemeProvider')
  return ctx
}

/** Kept for callers that only care about light/dark. */
export const useTheme = useDisplay
