// One-off codemod (already applied): rewrites fixed-pixel font sizes such as `text-[13.5px]` to rem
// (`text-[0.84375rem]`), and fixed-pixel layout sizes (column widths, offsets) the same way, so the
// "Text size" setting, which changes the root font size, scales the whole UI in proportion.
// Re-run it after adding new pixel sizes:  node scripts/px-to-rem.mjs
// ServiceMap.tsx is skipped on purpose: its SVG text is in viewBox units and scales with the drawing.
import { readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'

const SKIP = new Set(['ServiceMap.tsx'])
let changed = 0

function walk(dir) {
  for (const name of readdirSync(dir)) {
    const path = join(dir, name)
    if (statSync(path).isDirectory()) walk(path)
    else if (/\.tsx?$/.test(name) && !SKIP.has(name)) convert(path)
  }
}

function convert(path) {
  const before = readFileSync(path, 'utf8')
  const after = before.replace(/\btext-\[(\d+(?:\.\d+)?)px\]/g, (_, px) => {
    changed++
    return `text-[${Number((Number(px) / 16).toFixed(5))}rem]`
  })
  const rem = (px) => `${Number((Number(px) / 16).toFixed(5))}rem`
  // layout sizes: only real lengths (5px and up), never viewport maths, shadows or hairlines
  const final = after.replace(/(?<![\w-])-?(?:[a-z]+:)?(?:grid-cols|w|min-w|max-w|max-h|size|pl|mb|h|translate-x|left|bg)-\[[^\]\s]*\]/g, (token) => {
    if (/calc|100v|min\(/.test(token)) return token
    return token.replace(/(\d+(?:\.\d+)?)px/g, (m, px) => (Number(px) >= 5 ? (changed++, rem(px)) : m))
  })
  if (final !== before) writeFileSync(path, final)
}

walk(new URL('../src', import.meta.url).pathname)
console.log(`Converted ${changed} sizes to rem`)
