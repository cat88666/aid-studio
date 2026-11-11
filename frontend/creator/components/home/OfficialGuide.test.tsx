// @vitest-environment jsdom
import React, { act } from 'react'
import { createRoot } from 'react-dom/client'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import OfficialGuide from './OfficialGuide'
import { userGuideSections } from '~/content/guide-user'
import { developerGuideSections } from '~/content/guide-developer'
vi.mock('next/link', () => ({ default: ({ href, children, ...props }: React.AnchorHTMLAttributes<HTMLAnchorElement>) => <a href={href} {...props}>{children}</a> }))
let root: ReturnType<typeof createRoot>
let node: HTMLDivElement
beforeEach(() => {
  vi.stubGlobal('React', React)
  vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
  node = document.createElement('div')
  document.body.append(node)
  root = createRoot(node)
})
afterEach(async () => {
  await act(async () => root.unmount())
  node.remove()
  vi.unstubAllGlobals()
})
it('keeps both guide categories on-site', async () => {
  await act(async () => root.render(<OfficialGuide />))
  expect(node.querySelector('a[href="/guide/user/"]')).toBeTruthy()
  expect(node.querySelector('a[href="/guide/developer/"]')).toBeTruthy()
  for (const link of node.querySelectorAll('a')) expect(link.getAttribute('href')).toMatch(/^\/(?!\/)/)
})
it('renders every user chapter with working table-of-contents anchors', async () => {
  await act(async () => root.render(<OfficialGuide mode="user" />))
  for (const section of userGuideSections) {
    expect(node.querySelector(`article[id="${section.id}"]`)).toBeTruthy()
    expect(node.querySelector(`a[href="#${section.id}"]`)).toBeTruthy()
  }
  expect(new Set(userGuideSections.map(section => section.id)).size).toBe(userGuideSections.length)
})
it('searches chapters, reports empty results, and clears the filter', async () => {
  await act(async () => root.render(<OfficialGuide mode="developer" />))
  expect(node.querySelectorAll('article')).toHaveLength(developerGuideSections.length)
  expect(new Set(developerGuideSections.map(section => section.id)).size).toBe(developerGuideSections.length)
  for (const link of node.querySelectorAll('a')) expect(link.getAttribute('href')).toMatch(/^(\/(?!\/)|#)/)
  const input = node.querySelector('input')!
  const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!
  await act(async () => {
    setter.call(input, 'zz-no-matching-chapter-zz')
    input.dispatchEvent(new Event('input', { bubbles: true }))
  })
  expect(node.querySelectorAll('article')).toHaveLength(0)
  expect(node.textContent).toContain('没有找到相关章节')
  await act(async () => node.querySelector<HTMLButtonElement>('.official-guide__empty button')!.click())
  expect(node.querySelectorAll('article')).toHaveLength(developerGuideSections.length)
})
