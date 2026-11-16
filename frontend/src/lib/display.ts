// Display & accessibility settings: the data, its storage on this device, and how it is applied to
// the page. Pure functions (no React) so they are easy to test. The same rules run before first
// paint in public/theme-init.js - keep the two in step.

export type ThemeChoice = 'SYSTEM' | 'LIGHT' | 'DARK' | 'DIM' | 'WARM' | 'HIGH_CONTRAST'
export type TextSize = 'SMALL' | 'DEFAULT' | 'LARGE' | 'EXTRA_LARGE'
/** The palette actually on screen (SYSTEM resolves to light or dark). */
export type ThemeName = 'light' | 'dark' | 'dim' | 'warm' | 'contrast'

export interface DisplayPreferences {
  theme: ThemeChoice
  textSize: TextSize
  /** Always reduce animation; when off the device setting decides. */
  reduceMotion: boolean
  /** Wider line, letter and word spacing. */
  easierReading: boolean
  underlineLinks: boolean
}

export const DEFAULT_DISPLAY: DisplayPreferences = {
  theme: 'SYSTEM', textSize: 'DEFAULT', reduceMotion: false, easierReading: false, underlineLinks: false,
}

export const THEMES: { value: ThemeChoice; label: string; hint: string }[] = [
  { value: 'SYSTEM', label: 'Match system', hint: 'Follows your device' },
  { value: 'LIGHT', label: 'Light', hint: 'Bright, neutral' },
  { value: 'WARM', label: 'Warm', hint: 'Off-white paper, easier on the eyes' },
  { value: 'DIM', label: 'Dim', hint: 'Soft dark grey' },
  { value: 'DARK', label: 'Dark', hint: 'Deep, low glare' },
  { value: 'HIGH_CONTRAST', label: 'High contrast', hint: 'Strongest text and borders' },
]

export const TEXT_SIZES: { value: TextSize; label: string }[] = [
  { value: 'SMALL', label: 'Small' },
  { value: 'DEFAULT', label: 'Default' },
  { value: 'LARGE', label: 'Large' },
  { value: 'EXTRA_LARGE', label: 'Extra large' },
]

const KEY = 'riq-display'
const LEGACY_THEME_KEY = 'riq-theme'
const THEME_VALUES = THEMES.map((t) => t.value)
const SIZE_VALUES = TEXT_SIZES.map((t) => t.value)

/** Accepts anything (old storage, a server response) and returns a complete, valid set of preferences. */
export function normaliseDisplay(raw: unknown): DisplayPreferences {
  const r = (raw && typeof raw === 'object' ? raw : {}) as Record<string, unknown>
  return {
    theme: THEME_VALUES.includes(r.theme as ThemeChoice) ? (r.theme as ThemeChoice) : DEFAULT_DISPLAY.theme,
    textSize: SIZE_VALUES.includes(r.textSize as TextSize) ? (r.textSize as TextSize) : DEFAULT_DISPLAY.textSize,
    reduceMotion: r.reduceMotion === true,
    easierReading: r.easierReading === true,
    underlineLinks: r.underlineLinks === true,
  }
}

export function sameDisplay(a: DisplayPreferences, b: DisplayPreferences) {
  return a.theme === b.theme && a.textSize === b.textSize && a.reduceMotion === b.reduceMotion
    && a.easierReading === b.easierReading && a.underlineLinks === b.underlineLinks
}

export function readStoredDisplay(): DisplayPreferences {
  try {
    const stored = localStorage.getItem(KEY)
    if (stored) return normaliseDisplay(JSON.parse(stored))
    // before display settings existed only the theme was stored
    const legacy = localStorage.getItem(LEGACY_THEME_KEY)
    return normaliseDisplay({ theme: legacy?.toUpperCase() })
  } catch {
    return DEFAULT_DISPLAY
  }
}

export function storeDisplay(prefs: DisplayPreferences) {
  try { localStorage.setItem(KEY, JSON.stringify(prefs)) } catch { /* private mode: settings last for this visit only */ }
}

export function resolveTheme(choice: ThemeChoice, systemDark: boolean): ThemeName {
  switch (choice) {
    case 'LIGHT': return 'light'
    case 'DARK': return 'dark'
    case 'DIM': return 'dim'
    case 'WARM': return 'warm'
    case 'HIGH_CONTRAST': return 'contrast'
    default: return systemDark ? 'dark' : 'light'
  }
}

export const isDarkTheme = (name: ThemeName) => name === 'dark' || name === 'dim'

const SIZE_ATTR: Record<TextSize, string> = { SMALL: 'small', DEFAULT: 'default', LARGE: 'large', EXTRA_LARGE: 'xl' }

/** Writes the preferences onto <html> as a class and data attributes; index.css does the rest. */
export function applyDisplay(prefs: DisplayPreferences, systemDark: boolean, root: HTMLElement = document.documentElement) {
  const name = resolveTheme(prefs.theme, systemDark)
  root.dataset.theme = name
  root.classList.toggle('dark', isDarkTheme(name))
  root.dataset.textSize = SIZE_ATTR[prefs.textSize]
  toggleData(root, 'motion', prefs.reduceMotion ? 'reduce' : null)
  toggleData(root, 'reading', prefs.easierReading ? 'easy' : null)
  toggleData(root, 'links', prefs.underlineLinks ? 'underline' : null)
  return name
}

function toggleData(root: HTMLElement, key: string, value: string | null) {
  if (value) root.dataset[key] = value
  else delete root.dataset[key]
}
