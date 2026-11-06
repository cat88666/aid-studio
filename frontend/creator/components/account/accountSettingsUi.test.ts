import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

function readWorkspaceFile(path: string): string {
  return readFileSync(new URL(`../../${path}`, import.meta.url), 'utf-8')
}

describe('account settings UI contracts', () => {
  it('reuses AuthDarkField instead of native inputs for account forms', () => {
    const source = readWorkspaceFile('components/account/AccountSettingsFields.tsx')
    expect(source).toContain('AuthDarkField')
    expect(source).toContain('unlockAuthInputAutofill')
    expect(source).not.toMatch(/<input[\s>]/)
  })

  it('styles confirm panels with project cyan tokens rather than generic hex copies', () => {
    const css = readWorkspaceFile('components/account/account-settings.css')
    expect(css).toContain('--home-cyan')
    expect(css).toContain('--home-muted')
    expect(css).toContain('--home-grad')
    expect(css).toContain('--create-surface-modal')
  })

  it('keeps notices padded and does not fade the disabled cancel button away', () => {
    const css = readWorkspaceFile('components/account/account-settings.css')
    expect(css).toMatch(/account-settings__danger-notice[\s\S]{0,180}padding:\s*10px 14px/)
    expect(css).toMatch(/danger-submit:disabled[\s\S]{0,220}opacity:\s*1\s*!important/)
    expect(css).not.toMatch(/\.ant-btn:disabled[\s\S]{0,80}opacity:\s*0\.45/)
  })
})
