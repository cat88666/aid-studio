'use client'

import { useEffect, useRef, useState } from 'react'
import type { CreatorLocale } from '~/config/creatorLanguages'
import { useUserStore } from '~/stores/user'
import { hpay, safeCheckoutUrl, type HPayCatalog, type HPayOrder } from './api'
import './hpay.css'

const texts = {
  en: { title: 'Pay with USDT', loading: 'Loading…', unavailable: 'USDT payments are not available yet.', error: 'Unable to confirm payment. Retry shortly; do not pay twice.', retry: 'Retry', buy: 'Buy', credits: 'credits', checkout: 'Open secure checkout', refresh: 'Check payment', pending: 'Awaiting payment confirmation', paid: 'Paid · credits added', orders: 'Recent USDT orders (up to 20)', info: 'Prices are in USDT. Use only the network and exact amount shown at checkout. Credits are added after payment is confirmed.', unknown: 'Order submitted; checkout is not yet available. Check the order before trying again.', empty: 'No USDT credit packs available.', processing: 'Processing…' },
  zh: { title: 'USDT 充值', loading: '加载中…', unavailable: 'USDT 支付尚未开放。', error: '暂时无法确认付款，请稍后重试，不要重复付款。', retry: '重试', buy: '购买', credits: '积分', checkout: '打开支付收银台', refresh: '查询到账', pending: '等待付款确认', paid: '已支付 · 积分已到账', orders: '最近 USDT 订单（最多20笔）', info: '价格以 USDT 计。请按收银台显示的网络和准确金额付款，确认后积分自动到账。', unknown: '订单已提交，收银台暂不可用。请先查询订单，不要重复付款。', empty: '暂无 USDT 积分套餐。', processing: '处理中…' }
}

export default function HPayPanel({ locale }: { locale: CreatorLocale }) {
  const t = texts[locale === 'zh' || locale === 'zh-hant' ? 'zh' : 'en']
  const [catalog, setCatalog] = useState<HPayCatalog | null>(null)
  const [orders, setOrders] = useState<HPayOrder[]>([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(false)
  const [attempt, setAttempt] = useState(0)
  const alive = useRef(false)
  const inFlight = useRef(false)
  useEffect(() => {
    alive.current = true
    return () => { alive.current = false }
  }, [])
  useEffect(() => {
    let active = true
    setError(false)
    void hpay.catalog().then(async data => {
      if (!active) return
      setCatalog(data)
      if (data.enabled) {
        const list = await hpay.orders()
        if (active) setOrders(list)
      }
    }).catch(() => { if (active) setError(true) })
    return () => { active = false }
  }, [attempt])

  const update = (order: HPayOrder) => {
    setOrders(previous => [order, ...previous.filter(item => item.orderNo !== order.orderNo)].slice(0, 20))
    if (order.status === 'paid') void useUserStore.getState().fetchProfile()
  }
  async function run(action: () => Promise<HPayOrder>) {
    if (inFlight.current) return
    inFlight.current = true
    setBusy(true); setError(false)
    try {
      const order = await action()
      if (alive.current) update(order)
    } catch { if (alive.current) setError(true) }
    finally { inFlight.current = false; if (alive.current) setBusy(false) }
  }
  // Sequential polling; a timeout never changes the order into a paid state.
  const pendingNo = orders.find(order => order.status === 'pending')?.orderNo
  useEffect(() => {
    if (!pendingNo || busy || error || !catalog?.enabled) return
    const timer = window.setTimeout(() => { void run(() => hpay.query(pendingNo)) }, 15000)
    return () => window.clearTimeout(timer)
  }, [pendingNo, busy, error, catalog?.enabled])

  return <section className="hpay-panel" aria-labelledby="hpay-title">
    <h2 id="hpay-title">{t.title}</h2>
    {error && <p role="alert">{t.error} <button type="button" disabled={busy} onClick={() => setAttempt(n => n + 1)}>{t.retry}</button></p>}
    {!catalog ? <p role="status">{t.loading}</p> : !catalog.enabled ? <p>{t.unavailable}</p> : <>
      <p>{t.info}</p>
      {!catalog.packages.length && <p>{t.empty}</p>}
      <div className="hpay-packs">{catalog.packages.map(pack => <article key={pack.id}>
        <h3>{pack.name}</h3><p>{pack.credits} {t.credits}</p><strong>{Number(pack.amount).toFixed(2)} USDT</strong>
        <button type="button" disabled={busy} onClick={() => { void run(() => hpay.create(pack.id)) }}>{busy ? t.processing : t.buy}</button>
      </article>)}</div>
      {!!orders.length && <h3>{t.orders}</h3>}
      <ul className="hpay-orders">{orders.map(order => {
        const checkout = safeCheckoutUrl(order.checkoutUrl)
        return <li key={order.orderNo}>
          <div><strong>{order.productName} · {Number(order.amount).toFixed(2)} USDT</strong><small>{order.orderNo}</small></div>
          <p role="status">{order.status === 'paid' ? t.paid : t.pending}</p>
          {order.status === 'pending' && <div>
            {checkout ? <a href={checkout} target="_blank" rel="noopener noreferrer">{t.checkout}</a> : <p>{t.unknown}</p>}
            <button type="button" disabled={busy} onClick={() => { void run(() => hpay.query(order.orderNo)) }}>{t.refresh}</button>
          </div>}
        </li>
      })}</ul>
    </>}
  </section>
}
