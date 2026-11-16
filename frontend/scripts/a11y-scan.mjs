// Accessibility gate: runs axe-core (WCAG 2.1 A/AA + best practices) over every screen, for every
// role, in every theme, and checks that nothing overflows sideways at the largest text size.
//
//   1. start the app with fresh demo data:  cd backend && ./mvnw spring-boot:run
//   2. npm i --no-save playwright axe-core   (Chrome must be installed)
//   3. node scripts/a11y-scan.mjs [baseUrl] [screenshotDir]
//
// Exits non-zero if any violation is found.
import { createRequire } from 'node:module'
import { readFileSync, mkdirSync } from 'node:fs'

const require = createRequire(process.env.A11Y_MODULES ? `${process.env.A11Y_MODULES}/` : import.meta.url)
const { chromium } = require('playwright')
const axeSource = readFileSync(require.resolve('axe-core/axe.min.js'), 'utf8')

const BASE = process.argv[2] ?? 'http://localhost:8080'
const SHOTS = process.argv[3]
const PASSWORD = 'Password@123'
const THEMES = ['LIGHT', 'DARK', 'DIM', 'WARM', 'HIGH_CONTRAST']
const DEFAULTS = { textSize: 'DEFAULT', reduceMotion: true, easierReading: false, underlineLinks: false }
// the last pass stresses layout: largest text, wider spacing, underlined links
const PASSES = [...THEMES.map((theme) => ({ name: theme.toLowerCase(), prefs: { ...DEFAULTS, theme } })),
  { name: 'xl-easy-reading', prefs: { theme: 'LIGHT', textSize: 'EXTRA_LARGE', reduceMotion: true, easierReading: true, underlineLinks: true } }]

const ROLES = [
  { user: null, pages: ['/login', '/register', '/forgot-password'] },
  { user: 'ava', pages: ['/', '/report', '/assistant', '/incidents', '/incidents/:first', '/profile', '/profile?tab=security', '/profile?tab=display', '/kb', '/kb/:article'] },
  { user: 'rahul', pages: ['/', '/queue', '/incidents/:first', '/kb/new'] },
  { user: 'manager', pages: ['/', '/incidents', '/services', '/reports', '/admin/users', '/admin/catalogue', '/admin/sla', '/admin/settings', '/admin/audit', '/admin/outbox', '/admin/security'] },
]

const browser = await chromium.launch({ channel: 'chrome' })
let violations = 0, scanned = 0
const overflow = []

for (const role of ROLES) {
  const context = await browser.newContext({ viewport: { width: 1366, height: 850 } })
  const page = await context.newPage()
  let token = null, first = null, article = null
  if (role.user) {
    const login = await context.request.post(`${BASE}/api/auth/login`, { data: { email: `${role.user}@resolveiq.test`, password: PASSWORD } })
    token = (await login.json()).token
    const auth = { Authorization: `Bearer ${token}` }
    first = (await (await context.request.get(`${BASE}/api/incidents?size=1`, { headers: auth })).json()).items?.[0]?.number
    const kb = await (await context.request.get(`${BASE}/api/knowledge`, { headers: auth })).json()
    article = (kb.items ?? kb)[0]?.id
    // the sign-in above set the HttpOnly refresh cookie in this browser context, so pages open signed in
  }
  for (const pass of PASSES) {
    if (token) await context.request.put(`${BASE}/api/auth/preferences`, { headers: { Authorization: `Bearer ${token}` }, data: pass.prefs })
    await page.addInitScript((p) => localStorage.setItem('riq-display', JSON.stringify(p)), pass.prefs)
    for (const template of role.pages) {
      const path = template.replace(':first', first).replace(':article', article)
      await page.goto(`${BASE}${path}`)
      await page.waitForLoadState('networkidle').catch(() => {})
      await page.waitForTimeout(350)
      await page.evaluate(axeSource)
      const result = await page.evaluate(() => window.axe.run(document, { runOnly: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'best-practice'] }))
      scanned++
      for (const v of result.violations) {
        violations += v.nodes.length
        console.log(`✗ ${role.user ?? 'anonymous'} ${path} [${pass.name}] ${v.id}: ${v.help} (${v.nodes.length})`)
        v.nodes.slice(0, 3).forEach((n) => console.log(`    ${n.target.join(' ')}  ${n.any?.[0]?.message ?? ''}`.slice(0, 260)))
      }
      const wide = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
      if (wide > 2) overflow.push(`${role.user ?? 'anonymous'} ${path} [${pass.name}] overflows by ${wide}px`)
      if (SHOTS) {
        mkdirSync(SHOTS, { recursive: true })
        await page.screenshot({ path: `${SHOTS}/${role.user ?? 'anon'}${path.replace(/[/?=:]/g, '_')}-${pass.name}.png` })
      }
    }
  }
  await context.close()
}
await browser.close()
overflow.forEach((o) => console.log(`↔ ${o}`))
console.log(`\n${scanned} screen/theme combinations scanned: ${violations} accessibility violations, ${overflow.length} sideways overflows`)
process.exit(violations || overflow.length ? 1 : 0)
