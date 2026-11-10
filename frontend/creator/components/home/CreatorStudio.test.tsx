// @vitest-environment jsdom
import React, { act } from 'react'
import { createRoot } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import CreatorStudio from './CreatorStudio'

const mocks = vi.hoisted(() => ({ user: { token: 'token-a', user: { id: 'user-a' } as { id: string } | null }, login: vi.fn(), models: vi.fn() }))
vi.mock('./CreatorDashboard', () => ({ default: () => null }))
vi.mock('~/hooks/useCreatorLocale', () => ({ useCreatorLocale: () => ({ locale: 'en' }) }))
vi.mock('~/stores/user', () => ({ useUserStore: (selector: (state: typeof mocks.user) => unknown) => selector(mocks.user) }))
vi.mock('~/utils/api', () => ({ buildUserApiAuthHeaders: () => ({}), resolveClientApiUrl: (url: string) => url }))
vi.mock('~/utils/authLoginNavigation', () => ({ requireLogin: mocks.login, clearAuthSessionArtifacts: vi.fn() }))
vi.mock('~/utils/business/model', () => ({ userModelList: mocks.models }))
const modelOptions = [
  { id: 157, modelCode: 'creator-elysium-chat', modelName: 'Elysium Chat', modelType: 'text', isFree: true },
  { id: 158, modelCode: 'creator-free-openrouter-chat', modelName: 'OpenRouter Free', modelType: 'text', isFree: true },
  { id: 159, modelCode: 'paid-chat', modelName: 'Paid Model', modelType: 'text', isFree: false, costCredits: 0 },
]
const key = (id: string) => `creator-chat:v1:${id}`
const history = [{ role: 'user', content: 'Remember my story' }, { role: 'assistant', content: 'Your story is saved' }]
const record = (pending = false) => JSON.stringify({ version: 1, messages: history, draft: 'Continue it', pending })
let root: ReturnType<typeof createRoot> | undefined
let node: HTMLDivElement
beforeEach(() => {
  vi.stubGlobal('React', React)
  vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
  Element.prototype.scrollIntoView = vi.fn()
  localStorage.clear()
  mocks.models.mockReset().mockResolvedValue(modelOptions)
  mocks.user = { token: 'token-a', user: { id: 'user-a' } }
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('event: delta\ndata: {"content":"Next chapter"}\n\nevent: done\ndata: {}\n\n', { headers: { 'content-type': 'text/event-stream' } })))
})
afterEach(async () => {
  if (root) await act(async () => root?.unmount())
  root = undefined
  document.body.replaceChildren()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})
async function mount() {
  node = document.createElement('div'); document.body.append(node)
  root = createRoot(node)
  await act(async () => root?.render(React.createElement(CreatorStudio)))
}
async function click(label: string) {
  const button = Array.from(node.querySelectorAll('button')).find(item => item.textContent === label)
  expect(button).toBeTruthy()
  await act(async () => button?.click())
}
async function submit() {
  await act(async () => node.querySelector('form')?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })))
}
describe('Creator chat browser persistence', () => {
  it('restores history and draft after remount, then sends the full context', async () => {
    localStorage.setItem(key('user-a'), record())
    await mount()
    expect(node.textContent).toContain('Your story is saved')
    expect(node.querySelector('textarea')?.value).toBe('Continue it')
    await submit()
    expect(JSON.parse(vi.mocked(fetch).mock.calls[0][1]?.body as string).messages).toEqual([...history, { role: 'user', content: 'Continue it' }])
    await act(async () => root?.unmount()); root = undefined
    await mount()
    expect(node.textContent).toContain('Next chapter')
    expect(vi.mocked(fetch)).toHaveBeenCalledTimes(1)
  })
  it('restores across tabs and keeps users and token refreshes isolated', async () => {
    localStorage.setItem(key('user-a'), record())
    await mount(); await click('Image'); await click('AI chat')
    expect(node.textContent).toContain('Your story is saved')
    mocks.user.token = 'renewed-token'
    await act(async () => root?.render(React.createElement(CreatorStudio)))
    expect(node.textContent).toContain('Your story is saved')
    mocks.user = { token: 'token-b', user: { id: 'user-b' } }
    await act(async () => root?.render(React.createElement(CreatorStudio)))
    expect(node.textContent).not.toContain('Your story is saved')
    expect(localStorage.getItem(key('user-a'))).toBe(record())
    expect(localStorage.getItem(key('user-b'))).toBeNull()
  })
  it('clears only this user and stays empty after remount', async () => {
    localStorage.setItem(key('user-a'), record()); localStorage.setItem(key('user-b'), record())
    await mount(); await click('New chat')
    expect(localStorage.getItem(key('user-a'))).toBeNull()
    expect(localStorage.getItem(key('user-b'))).toBe(record())
    await click('Image'); await click('AI chat')
    expect(node.textContent).not.toContain('Your story is saved')
  })
  it.each(['{bad', JSON.stringify({ version: 1, messages: [{ role: 'system', content: 'bad' }], draft: '', pending: false })])('rejects corrupt data without overwriting it', async raw => {
    localStorage.setItem(key('user-a'), raw)
    await mount()
    expect(node.textContent).toContain('Saved chat data is invalid')
    expect(localStorage.getItem(key('user-a'))).toBe(raw)
    expect(fetch).not.toHaveBeenCalled()
  })
  it('shows interrupted pending replies without resending', async () => {
    localStorage.setItem(key('user-a'), record(true))
    await mount()
    expect(node.textContent).toContain('Connection ended before the reply completed')
    expect(node.querySelector('textarea')?.disabled).toBe(false)
    expect(fetch).not.toHaveBeenCalled()
  })
  it('does not read, overwrite, or send before stable identity exists', async () => {
    localStorage.setItem(key('user-a'), record())
    mocks.user.user = null
    const read = vi.spyOn(Storage.prototype, 'getItem')
    const write = vi.spyOn(Storage.prototype, 'setItem')
    await mount(); await submit()
    expect(read).not.toHaveBeenCalled(); expect(write).not.toHaveBeenCalled(); expect(fetch).not.toHaveBeenCalled()
    expect(node.querySelector('textarea')?.disabled).toBe(true)
  })
  it('reports storage failures visibly while retaining the conversation in memory', async () => {
    localStorage.setItem(key('user-a'), record())
    await mount()
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('quota') })
    await submit()
    expect(node.textContent).toContain('This browser could not save or restore your chat')
    expect(node.textContent).toContain('Next chapter')
  })
})

async function chooseModel(value: string) {
  await act(async () => {
    const select = node.querySelector('select')!
    select.value = value
    select.dispatchEvent(new Event('change', { bubbles: true }))
  })
}

describe('Creator model selection', () => {
  it('sends the chosen model, restores it per user, and preserves old answer labels', async () => {
    localStorage.setItem(key('user-a'), record())
    await mount()
    expect(mocks.models).toHaveBeenCalledWith({ modelType: 'text', generateMode: 'text' })
    await chooseModel('creator-free-openrouter-chat')
    expect(node.querySelector('.is-assistant strong')?.textContent).toBe('Elysium Chat')
    await submit()
    const request = JSON.parse(vi.mocked(fetch).mock.calls[0][1]?.body as string)
    expect(request.modelCode).toBe('creator-free-openrouter-chat')
    expect(request.messages.every((item: object) => Object.keys(item).sort().join(',') === 'content,role')).toBe(true)
    expect(node.querySelectorAll('.is-assistant strong')[1]?.textContent).toBe('OpenRouter Free')
    await act(async () => root?.unmount()); root = undefined
    await mount()
    expect(node.querySelector('select')?.value).toBe('creator-free-openrouter-chat')
    mocks.user = { token: 'token-b', user: { id: 'user-b' } }
    await act(async () => root?.render(React.createElement(CreatorStudio)))
    expect(node.querySelector('select')?.value).toBe('creator-elysium-chat')
  })
  it('blocks sending after catalog errors and permits retry without guessing a model', async () => {
    localStorage.setItem(key('user-a'), record())
    mocks.models.mockRejectedValueOnce(new Error('offline'))
    await mount(); await submit()
    expect(node.textContent).toContain('Could not load models')
    expect(fetch).not.toHaveBeenCalled()
    await click('Retry')
    expect(node.querySelector('select')?.disabled).toBe(false)
    expect(node.querySelector('option[value="paid-chat"]')?.textContent).toContain('Usage pricing')
    expect(node.querySelector('option[value="paid-chat"]')?.textContent).not.toContain('0 credits')
  })
  it('does not silently replace a removed saved model with another model', async () => {
    localStorage.setItem(key('user-a'), JSON.stringify({ version: 1, messages: history, draft: 'Next', pending: false, modelCode: 'removed-chat' }))
    await mount(); await submit()
    expect(node.textContent).toContain('The saved model is unavailable')
    expect(fetch).not.toHaveBeenCalled()
  })
  it('locks selection during streaming and saves the request model on its reply', async () => {
    localStorage.setItem(key('user-a'), record())
    vi.mocked(fetch).mockResolvedValue(new Response(new ReadableStream(), { headers: { 'content-type': 'text/event-stream' } }))
    await mount()
    await act(async () => { node.querySelector('form')?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); await Promise.resolve() })
    expect(node.querySelector('select')?.disabled).toBe(true)
    await click('Stop')
    expect(node.querySelector('select')?.disabled).toBe(false)
  })
  it('offers capability-specific media choices while keeping unconnected generation disabled', async () => {
    mocks.models.mockImplementation(async ({ modelType }: { modelType: string }) => modelType === 'text' ? modelOptions : [
      { id: 1, modelCode: 'video-basic', modelName: 'Video Basic', modelType: 'video' },
      { id: 2, modelCode: 'video-reference', modelName: 'Video Reference', modelType: 'video', capability: { supportsVideoInput: true } },
    ])
    await mount(); await click('Video remix')
    expect(mocks.models).toHaveBeenLastCalledWith({ modelType: 'video' })
    expect(node.querySelector('option[value="video-basic"]')).toBeNull()
    expect(node.querySelector('option[value="video-reference"]')).not.toBeNull()
    expect(node.querySelector('.creator-studio-unavailable button')?.hasAttribute('disabled')).toBe(true)
  })
})
