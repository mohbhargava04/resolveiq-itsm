import '@testing-library/jest-dom/vitest'
import { afterEach } from 'vitest'
import { cleanup } from '@testing-library/react'

afterEach(() => {
  cleanup()
  localStorage.clear()
  document.cookie.split('; ').forEach((c) => { document.cookie = `${c.split('=')[0]}=; max-age=0; path=/` })
})

// jsdom has no matchMedia; the theme provider asks whether the system prefers dark mode
// (a plain function, not vi.fn, so restoreMocks between tests cannot wipe its behaviour)
if (!window.matchMedia) {
  const noop = () => {}
  Object.defineProperty(window, 'matchMedia', {
    value: (query: string) => ({
      matches: false, media: query, onchange: null,
      addEventListener: noop, removeEventListener: noop, addListener: noop, removeListener: noop, dispatchEvent: () => false,
    }),
  })
}
