// @vitest-environment jsdom
import React, { act } from 'react'
import { createRoot } from 'react-dom/client'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import HPayPanel from './HPayPanel'
import { hpay, safeCheckoutUrl } from './api'
const profile = vi.hoisted(() => vi.fn())
vi.mock('~/stores/user', () => ({ useUserStore: { getState: () => ({ fetchProfile: profile }) } }))
vi.mock('./api', async original => ({ ...await original<typeof import('./api')>(), hpay: { catalog: vi.fn(), orders: vi.fn(), create: vi.fn(), query: vi.fn() } }))
let root: ReturnType<typeof createRoot>
let node: HTMLDivElement
const order = { orderNo: 'HPU1', productName: 'Starter', credits: 100, amount: 2, currency: 'USDT' as const, status: 'pending' as const, checkoutUrl: 'https://cashier.example/pay', createdAt: '' }
beforeEach(() => {
  vi.stubGlobal('React', React); vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true); vi.clearAllMocks()
  vi.mocked(hpay.catalog).mockResolvedValue({ enabled: true, currency: 'USDT', packages: [{ id: 1, name: 'Starter', credits: 100, amount: 2, currency: 'USDT' }] })
  vi.mocked(hpay.orders).mockResolvedValue([])
  node = document.createElement('div'); document.body.append(node); root = createRoot(node)
})
afterEach(async () => { await act(async () => root.unmount()); node.remove(); vi.unstubAllGlobals() })
async function render() { await act(async () => root.render(<HPayPanel locale="en" />)) }
function button(text: string) { return Array.from(node.querySelectorAll('button')).find(b => b.textContent === text)! }
it('keeps purchase unavailable until configured', async () => {
  vi.mocked(hpay.catalog).mockResolvedValue({ enabled: false, currency: 'USDT', packages: [] })
  await render(); expect(node.textContent).toContain('not available'); expect(button('Buy')).toBeUndefined(); expect(hpay.orders).not.toHaveBeenCalled()
})
it('shows USDT checkout without claiming credits before confirmation', async () => {
  vi.mocked(hpay.create).mockResolvedValue(order); await render(); await act(async () => button('Buy').click())
  expect(hpay.create).toHaveBeenCalledWith(1); expect(node.textContent).toContain('2.00 USDT')
  expect(node.querySelector('a')?.getAttribute('href')).toBe(order.checkoutUrl); expect(profile).not.toHaveBeenCalled()
})
it('refreshes balance only after confirmed payment', async () => {
  vi.mocked(hpay.orders).mockResolvedValue([order]); vi.mocked(hpay.query).mockResolvedValue({ ...order, status: 'paid' })
  await render(); await act(async () => button('Check payment').click())
  expect(profile).toHaveBeenCalledOnce(); expect(node.textContent).toContain('credits added'); expect(node.querySelector('a')).toBeNull()
})
it('preserves pending state when provider check fails', async () => {
  vi.mocked(hpay.orders).mockResolvedValue([order]); vi.mocked(hpay.query).mockRejectedValue(new Error())
  await render(); await act(async () => button('Check payment').click())
  expect(node.querySelector('[role="alert"]')).toBeTruthy(); expect(node.textContent).toContain('Awaiting payment'); expect(profile).not.toHaveBeenCalled()
})
it('handles unknown create outcome without fake payment link', async () => {
  vi.mocked(hpay.create).mockResolvedValue({ ...order, checkoutUrl: null }); await render(); await act(async () => button('Buy').click())
  expect(node.querySelector('a')).toBeNull(); expect(node.textContent).toContain('checkout is not yet available')
})
it('rejects unsafe checkout schemes', () => {
  for (const value of ['javascript:alert(1)', 'http://cashier.example', 'https://user:pass@cashier.example', '//evil.test']) expect(safeCheckoutUrl(value)).toBeNull()
})
