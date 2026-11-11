// @vitest-environment jsdom
import React, { act } from 'react'
import { createRoot } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import DashboardProjects from './DashboardProjects'
import { landingSamples } from '~/utils/landingSamples'
import { sampleLibrary } from '~/utils/sampleLibrary'

const mocks = vi.hoisted(() => ({ user: { token: '' }, list: vi.fn(), open: vi.fn(), login: vi.fn() }))
vi.mock('next/link', () => ({ default: ({ href, children, ...props }: React.ComponentProps<'a'>) => <a href={href} {...props}>{children}</a> }))
vi.mock('~/hooks/useCreatorLocale', () => ({ useCreatorLocale: () => ({ locale: 'zh' }) }))
vi.mock('~/stores/user', () => ({ useUserStore: (selector: (state: typeof mocks.user) => unknown) => selector(mocks.user) }))
vi.mock('~/hooks/useOpenLibraryProject', () => ({ useOpenLibraryProject: () => mocks.open }))
vi.mock('~/utils/business/project', () => ({ userProjectList: mocks.list }))
vi.mock('~/utils/authLoginNavigation', () => ({ requireLogin: mocks.login }))
let root: ReturnType<typeof createRoot>
let node: HTMLDivElement
beforeEach(() => {
  vi.stubGlobal('React', React)
  vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
  mocks.user.token = ''
  mocks.list.mockReset().mockResolvedValue({ total: 0, rows: [] })
  mocks.open.mockReset()
  mocks.login.mockReset()
  node = document.createElement('div')
  document.body.append(node)
  root = createRoot(node)
})
afterEach(async () => {
  await act(async () => root.unmount())
  document.body.replaceChildren()
  vi.unstubAllGlobals()
})
async function mount() {
  await act(async () => root.render(<DashboardProjects />))
}
function expectSamples() {
  const links = [...node.querySelectorAll('a[href^="/case?sample="]')]
  expect(links).toHaveLength(6)
  for (const sample of landingSamples) {
    expect(links.some(link => link.getAttribute('href') === `/case?sample=${sample.id}`)).toBe(true)
    expect(sampleLibrary.some(item => item.id === sample.id)).toBe(true)
  }
}
describe('project browser default samples', () => {
  it('shows the six existing sample links without requiring login or querying private projects', async () => {
    await mount()
    expectSamples()
    expect(node.querySelector('dialog')).toBeNull()
    expect(node.querySelector('main')).toBeTruthy()
    expect(node.querySelector('h1')?.textContent).toBe('项目')
    expect(node.querySelector('a[href="/start/"]')).toBeTruthy()
    expect(node.querySelector('a[href="/works/"]')).toBeTruthy()
    expect(mocks.list).not.toHaveBeenCalled()
    const login = [...node.querySelectorAll('button')].find(button => button.textContent === '登录查看')
    await act(async () => login?.click())
    expect(mocks.login).toHaveBeenCalledWith({ redirect: '/projects/', leaveAuthRoute: false })
  })
  it('keeps samples visible for an empty personal library', async () => {
    mocks.user.token = 'test-token'
    await mount()
    expectSamples()
    expect(node.textContent).toContain('暂无个人项目')
  })
  it('preserves personal project opening alongside samples', async () => {
    mocks.user.token = 'test-token'
    mocks.list.mockResolvedValue({ total: 1, rows: [{ id: 42, projectName: '我的电影', projectType: 'film' }] })
    await mount()
    expectSamples()
    const project = [...node.querySelectorAll('button')].find(button => button.textContent?.includes('我的电影'))
    expect(project).toBeTruthy()
    await act(async () => project?.click())
    expect(mocks.open).toHaveBeenCalledWith(42, 'film')
  })
  it('retains sample access when the personal project API fails', async () => {
    mocks.user.token = 'test-token'
    mocks.list.mockRejectedValue(new Error('offline'))
    await mount()
    expectSamples()
    expect(node.querySelector('[role="alert"]')).toBeTruthy()
  })
})
