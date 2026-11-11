// @vitest-environment jsdom
import React, { act } from 'react'
import { createRoot } from 'react-dom/client'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import HPayDemoPanel from './HPayDemoPanel'
let root: ReturnType<typeof createRoot>
let node: HTMLDivElement
beforeEach(() => {
  vi.stubGlobal('React', React); vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true)
  vi.stubGlobal('fetch', vi.fn(() => { throw new Error('Demo must not send requests') }))
  HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', '') }
  HTMLDialogElement.prototype.close = function () { this.removeAttribute('open') }
  node = document.createElement('div'); document.body.append(node); root = createRoot(node)
})
afterEach(async () => { await act(async () => root.unmount()); node.remove(); vi.unstubAllGlobals() })
const button = (text: string) => Array.from(node.querySelectorAll('button')).find(b => b.textContent === text)!
async function render() { await act(async () => root.render(<HPayDemoPanel locale="zh" />)) }
it('offers a visible demo payment without login or network requests', async () => {
  await render(); expect(node.textContent).toContain('模拟模式'); expect(node.querySelectorAll('article button')).toHaveLength(3)
  await act(async () => button('模拟支付').click())
  expect(node.querySelector('dialog[open]')).toBeTruthy(); expect(node.querySelector('dialog')?.textContent).toContain('1.00 USDT')
  expect(node.querySelector('dialog')?.textContent).toContain('请勿转账'); expect(fetch).not.toHaveBeenCalled()
})
it('simulates success and failure without changing real balances', async () => {
  await render(); await act(async () => button('模拟支付').click()); await act(async () => button('模拟支付成功').click())
  expect(node.querySelector('[role="status"]')?.textContent).toContain('实际积分余额没有变化')
  await act(async () => button('重新模拟').click()); await act(async () => button('模拟支付失败').click())
  expect(node.querySelector('dialog h2')?.textContent).toBe('模拟支付失败'); expect(fetch).not.toHaveBeenCalled()
})
it('closes and opens a new clean checkout for the selected amount', async () => {
  await render(); await act(async () => button('模拟支付').click()); await act(async () => button('模拟支付成功').click())
  await act(async () => button('关闭').click()); expect(node.querySelector('dialog')).toBeNull()
  await act(async () => node.querySelectorAll<HTMLButtonElement>('article button')[2].click())
  expect(node.querySelector('dialog h2')?.textContent).toBe('模拟收银台')
  expect(node.querySelector('dialog')?.textContent).toContain('50.00 USDT')
})
