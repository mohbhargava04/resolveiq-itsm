import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ChipsInput, EmptyState } from './ui'

function Skills({ initial = [] as string[] }) {
  const [value, setValue] = useState(initial)
  return (
    <>
      <label htmlFor="skills">Skills</label>
      <ChipsInput id="skills" value={value} onChange={setValue} />
      <output>{value.join('|')}</output>
    </>
  )
}

describe('UI building blocks', () => {
  it('FE-11 skill chips: Enter or comma adds, values are upper-cased and de-duplicated, Backspace removes', async () => {
    const user = userEvent.setup()
    render(<Skills initial={['VPN']} />)
    const input = screen.getByLabelText('Skills')
    await user.type(input, 'network,vpn{Enter}')
    expect(screen.getByRole('status')).toHaveTextContent('VPN|NETWORK')
    await user.click(screen.getByRole('button', { name: 'Remove VPN' }))
    expect(screen.getByRole('status')).toHaveTextContent(/^NETWORK$/)
    await user.type(input, '{Backspace}')
    expect(screen.getByRole('status')).toBeEmptyDOMElement()
  })

  it('FE-12 a full-page empty state can be the page heading (accessibility: every page has an h1)', () => {
    render(<EmptyState heading="h1" title="INC-9 doesn't exist" />)
    expect(screen.getByRole('heading', { level: 1, name: "INC-9 doesn't exist" })).toBeInTheDocument()
  })
})
