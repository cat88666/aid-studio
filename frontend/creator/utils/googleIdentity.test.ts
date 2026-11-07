// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

beforeEach(() => { vi.resetModules(); delete window.google })
afterEach(() => { document.head.querySelectorAll('script').forEach(script => script.remove()); delete window.google; vi.useRealTimers() })

describe('Google GIS script loader', () => {
  it('shares one script load across simultaneous callers', async () => {
    const {loadGoogleIdentity} = await import('./googleIdentity')
    const first = loadGoogleIdentity(); const second = loadGoogleIdentity()
    expect(first).toBe(second)
    const identity = {initialize:vi.fn(),renderButton:vi.fn()}
    window.google = {accounts:{id:identity}}
    document.head.querySelector('script')!.dispatchEvent(new Event('load'))
    expect(await first).toBe(identity)
    expect(document.head.querySelectorAll('script')).toHaveLength(1)
  })
  it('removes failed scripts so a later retry can succeed', async () => {
    const {loadGoogleIdentity} = await import('./googleIdentity')
    const first = loadGoogleIdentity(); const failed = expect(first).rejects.toThrow()
    document.head.querySelector('script')!.dispatchEvent(new Event('error'))
    await failed
    expect(document.head.querySelectorAll('script')).toHaveLength(0)
    const second = loadGoogleIdentity()
    window.google = {accounts:{id:{initialize:vi.fn(),renderButton:vi.fn()}}}
    document.head.querySelector('script')!.dispatchEvent(new Event('load'))
    await expect(second).resolves.toBe(window.google.accounts!.id)
  })
  it('fails a stalled load instead of leaving a permanent loading state', async () => {
    vi.useFakeTimers()
    const {loadGoogleIdentity} = await import('./googleIdentity')
    const failed = expect(loadGoogleIdentity()).rejects.toThrow()
    vi.advanceTimersByTime(15000)
    await failed
    expect(document.head.querySelectorAll('script')).toHaveLength(0)
  })
})
