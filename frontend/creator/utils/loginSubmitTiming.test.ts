import { describe, expect, it, vi } from 'vitest'
import { waitForLoginMinimumLoading } from './loginSubmitTiming'

describe('waitForLoginMinimumLoading', () => {
  it('keeps a fast successful login loading for at least one second', async () => {
    vi.useFakeTimers()
    const wait = waitForLoginMinimumLoading(100, () => 350)
    let resolved = false
    void wait.then(() => {
      resolved = true
    })

    await vi.advanceTimersByTimeAsync(749)
    expect(resolved).toBe(false)
    await vi.advanceTimersByTimeAsync(1)
    await wait
    expect(resolved).toBe(true)
    vi.useRealTimers()
  })

  it('does not delay a login request that already took one second', async () => {
    vi.useFakeTimers()
    await waitForLoginMinimumLoading(100, () => 1100)
    expect(vi.getTimerCount()).toBe(0)
    vi.useRealTimers()
  })
})
