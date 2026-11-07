// @vitest-environment jsdom
import { act, createElement } from 'react'
import { createRoot } from 'react-dom/client'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useAuthSendCode } from './useAuthSendCode'

const mocks = vi.hoisted(() => ({
  send: vi.fn().mockResolvedValue(undefined),
  error: vi.fn(),
  config: { captchaEnabled: false, captchaType: '', smsLoginEnabled: true, emailLoginEnabled: true, getSendCodeIntervalSeconds: () => 60 }
}))
vi.mock('antd', () => ({ message: { error: mocks.error, success: vi.fn() } }))
vi.mock('~/hooks/useAuthText', () => ({ useAuthText: () => (text: string) => text }))
vi.mock('@/hooks/useAuthPublicConfig', () => ({ useAuthPublicConfig: () => mocks.config }))
vi.mock('~/utils/businessApi', () => ({ authSendCode: mocks.send }))
vi.mock('@/components/login/usePersistedCountdown', () => ({ usePersistedCountdown: () => ({ remaining: 0, restore: vi.fn(), start: vi.fn(), stop: vi.fn() }) }))
vi.mock('@/hooks/useBehaviorCaptcha', () => ({ useBehaviorCaptcha: () => ({ opening: false, isOpening: () => false, destroyActive: vi.fn() }) }))

const roots: ReturnType<typeof createRoot>[] = []
afterEach(async () => {
  for (const root of roots.splice(0)) await act(async () => root.unmount())
  document.body.replaceChildren()
  vi.clearAllMocks()
  mocks.config.smsLoginEnabled = true
  mocks.config.emailLoginEnabled = true
})

async function mount(account: string, selectedChannel: 'sms' | 'email') {
  let result!: ReturnType<typeof useAuthSendCode>
  function Harness() {
    result = useAuthSendCode({ account, selectedChannel, scene: 'login', countdownScope: 'test', captchaBindEl: '#captcha' })
    return null
  }
  const node = document.createElement('div')
  document.body.append(node)
  const root = createRoot(node)
  roots.push(root)
  await act(async () => root.render(createElement(Harness)))
  return () => result
}

describe('explicit registration channel', () => {
  it.each([
    ['sms', 'person@example.com'],
    ['email', '13800138000']
  ] as const)('never sends another channel when %s was chosen', async (channel, account) => {
    const result = await mount(account, channel)
    await act(async () => result().handleSendCode())
    expect(mocks.send).not.toHaveBeenCalled()
    expect(mocks.error).toHaveBeenCalled()
  })
  it('sends the selected email channel for a valid email', async () => {
    const result = await mount('person@example.com', 'email')
    await act(async () => result().handleSendCode())
    expect(mocks.send).toHaveBeenCalledWith(expect.objectContaining({ target: 'person@example.com', codeType: 'email', scene: 'login' }), undefined)
  })
  it('does not fall back to sms if the chosen email channel is disabled', async () => {
    mocks.config.emailLoginEnabled = false
    const result = await mount('13800138000', 'email')
    await act(async () => result().handleSendCode())
    expect(result().activeChannel).toBeNull()
    expect(mocks.send).not.toHaveBeenCalled()
  })
})
