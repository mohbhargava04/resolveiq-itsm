// Checks every theme's text colours against every surface they are used on.
// WCAG AA (4.5:1) for all themes; AAA (7:1) for the high-contrast theme.   node scripts/check-contrast.mjs
import { readFileSync } from 'node:fs'

const css = readFileSync(new URL('../src/index.css', import.meta.url), 'utf8')
const blocks = { light: ':root', dark: '.dark', dim: '[data-theme="dim"]', warm: '[data-theme="warm"]', contrast: '[data-theme="contrast"]' }

function tokens(selector) {
  const start = css.indexOf(`\n${selector} {`)
  const body = css.slice(start, css.indexOf('\n}', start))
  return Object.fromEntries([...body.matchAll(/--([\w-]+):\s*(#[0-9a-fA-F]{6})/g)].map((m) => [m[1], m[2]]))
}
const lum = (hex) => {
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255)
    .map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4))
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}
const ratio = (a, b) => { const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p); return (x + 0.05) / (y + 0.05) }

const SURFACES = ['bg', 'surface', 'surface-2', 'surface-3']
const pairs = [
  ...['ink', 'ink-2', 'muted', 'faint', 'accent-text', 'p1', 'p2', 'p3', 'p4', 'ok'].flatMap((t) => SURFACES.map((s) => [t, s])),
  ['accent-text', 'accent-soft'], ['ink', 'accent-soft'], ['p1', 'p1-soft'], ['p2', 'p2-soft'], ['p3', 'p3-soft'],
  ['p4', 'p4-soft'], ['ok', 'ok-soft'], ['ink', 'p1-soft'], ['ink', 'p2-soft'], ['muted', 'p1-soft'],
  ['accent-ink', 'accent'], ['accent-ink', 'accent-hover'], ['p1-ink', 'p1'], ['p2-ink', 'p2'],
]
let failures = 0
for (const [name, selector] of Object.entries(blocks)) {
  const t = { ...tokens(':root'), ...(name === 'dim' ? tokens('.dark') : {}), ...tokens(selector) }
  const need = name === 'contrast' ? 7 : 4.5
  let worst = Infinity
  for (const [fg, bg] of pairs) {
    const r = ratio(t[fg], t[bg])
    worst = Math.min(worst, r)
    if (r < need) { failures++; console.log(`  FAIL ${name}: ${fg} on ${bg} = ${r.toFixed(2)} (needs ${need})`) }
  }
  console.log(`${name.padEnd(9)} lowest ratio ${worst.toFixed(2)} (needs ${need})`)
}
process.exit(failures ? 1 : 0)
