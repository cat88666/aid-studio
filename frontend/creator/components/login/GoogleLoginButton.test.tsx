// @vitest-environment jsdom
import { act, createElement } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { GoogleLoginButton } from './GoogleLoginButton'

const mocks = vi.hoisted(() => ({
  load: vi.fn(), challenge: vi.fn(), login: vi.fn(), initialize: vi.fn(), renderButton: vi.fn(), success: vi.fn()
}))
vi.mock('~/hooks/useAuthText', () => ({ useAuthText: () => (text: string) => text }))
vi.mock('~/hooks/useCreatorLocale', () => ({ useCreatorLocale: () => ({ locale: 'en' }) }))
vi.mock('~/utils/googleIdentity', () => ({ loadGoogleIdentity: mocks.load }))
vi.mock('~/utils/businessApi', () => ({ authGoogleChallenge: mocks.challenge, authLogin: mocks.login }))
let root: Root
let node: HTMLDivElement
beforeEach(() => {
  vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
  mocks.load.mockResolvedValue({ initialize: mocks.initialize, renderButton: mocks.renderButton })
  mocks.challenge.mockResolvedValue({ nonce: 'nonce-one', expiresIn: 300 })
  mocks.login.mockResolvedValue({ token: 'test-token' })
  node = document.createElement('div'); document.body.append(node); root = createRoot(node)
})
afterEach(async () => {
  await act(async () => root.unmount())
  node.remove(); vi.resetAllMocks(); vi.useRealTimers(); vi.unstubAllGlobals()
})
async function render(enabled = true) {
  await act(async () => root.render(createElement(GoogleLoginButton, { enabled, clientId: 'client-id', inviteCode: 'INVITE01', onSuccess: mocks.success })))
}
const callback = () => mocks.initialize.mock.calls.at(-1)![0].callback as (response: {credential: string}) => void

describe('Google sign-in lifecycle', () => {
  it('does not load Google or fetch challenges when disabled', async () => {
    await render(false)
    expect(mocks.load).not.toHaveBeenCalled(); expect(mocks.challenge).not.toHaveBeenCalled(); expect(node.textContent).toBe('')
  })
  it('renders the official button with the server nonce and ignores duplicate callbacks', async () => {
    await render()
    expect(mocks.initialize).toHaveBeenCalledWith(expect.objectContaining({ client_id: 'client-id', nonce: 'nonce-one', auto_select: false }))
    expect(mocks.renderButton).toHaveBeenCalledWith(expect.any(HTMLElement), expect.objectContaining({ locale: 'en', text: 'continue_with' }))
    await act(async () => { callback()({credential:'id-token'}); callback()({credential:'id-token'}) })
    expect(mocks.login).toHaveBeenCalledTimes(1)
    expect(mocks.login).toHaveBeenCalledWith({loginType:'google',credential:'id-token',googleNonce:'nonce-one',inviteCode:'INVITE01'})
    expect(mocks.success).toHaveBeenCalledWith({token:'test-token'})
  })
  it('does not submit callbacks after closing and does not complete an in-flight login after closing', async () => {
    let resolve!: (data: {token: string}) => void
    mocks.login.mockImplementation(() => new Promise(r => {resolve = r}))
    await render(); const stale = callback()
    await act(async () => stale({credential:'id-token'}))
    await render(false)
    await act(async () => {stale({credential:'another-token'}); resolve({token:'late-token'})})
    expect(mocks.login).toHaveBeenCalledTimes(1); expect(mocks.success).not.toHaveBeenCalled()
  })
  it('fetches a fresh challenge when retrying a failed login', async () => {
    mocks.login.mockRejectedValue(new Error('失败'))
    await render(); await act(async () => callback()({credential:'id-token'}))
    expect(node.querySelector('[role=alert]')).not.toBeNull()
    mocks.challenge.mockResolvedValue({nonce:'nonce-two',expiresIn:300})
    await act(async () => (node.querySelector('button') as HTMLButtonElement).click())
    expect(mocks.challenge).toHaveBeenCalledTimes(2)
    expect(mocks.initialize).toHaveBeenLastCalledWith(expect.objectContaining({nonce:'nonce-two'}))
  })
  it('shows a retry when the challenge request fails, without rendering a button', async () => {
    mocks.challenge.mockRejectedValue(new Error('network unavailable'))
    await render()
    expect(mocks.renderButton).not.toHaveBeenCalled()
    expect(node.querySelector('[role=alert]')).not.toBeNull()
    expect(mocks.login).not.toHaveBeenCalled()
  })
  it('rejects expired credentials locally and offers a fresh attempt', async () => {
    vi.useFakeTimers(); mocks.challenge.mockResolvedValue({nonce:'nonce-one',expiresIn:1})
    await render(); const stale = callback()
    await act(async () => {vi.advanceTimersByTime(1001); stale({credential:'expired-token'})})
    expect(mocks.login).not.toHaveBeenCalled()
    expect(node.textContent).toContain('Google 登录已过期，请重试')
  })
})
