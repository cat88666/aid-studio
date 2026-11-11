// @vitest-environment jsdom
import { act, createElement } from 'react'
import { createRoot } from 'react-dom/client'
import { expect, it, vi } from 'vitest'
import { useHomeSidebarExtraNav } from './useHomeSidebarExtraNav'

const mocks = vi.hoisted(() => ({ push: vi.fn(), loadConfig: vi.fn().mockResolvedValue({ basic: { tutorial_url: 'https://external.example/old-guide' } }) }))
vi.mock('next/navigation', () => ({ useRouter: () => ({ push: mocks.push }) }))
vi.mock('@/hooks/useAuthPublicConfig', () => ({ loadPublicConfig: mocks.loadConfig }))

it('opens the local guide without requesting the legacy external tutorial configuration', async () => {
  const open = vi.spyOn(window, 'open').mockImplementation(() => null)
  const element = document.createElement('div')
  const root = createRoot(element)
  function Harness() {
    const { openTutorial } = useHomeSidebarExtraNav()
    return createElement('button', { onClick: openTutorial }, '教程')
  }
  try {
    await act(async () => root.render(createElement(Harness)))
    await act(async () => element.querySelector('button')!.click())
    expect(mocks.push).toHaveBeenCalledWith('/guide/')
    expect(mocks.loadConfig).not.toHaveBeenCalled()
    expect(open).not.toHaveBeenCalled()
  } finally {
    await act(async () => root.unmount())
    open.mockRestore()
  }
})
