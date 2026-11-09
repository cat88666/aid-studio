'use client'

import { useEffect, useState } from 'react'
import BillingPanel from '~/components/home/BillingPanel'
import { LandingBilling } from '~/components/home/LandingBilling'
import { useAuthPublicConfig } from '~/hooks/useAuthPublicConfig'
import { useCreatorLocale } from '~/hooks/useCreatorLocale'
import { creatorLanguage, type CreatorLocale } from '~/config/creatorLanguages'
import { useUserStore } from '~/stores/user'
import type { RechargeOrderRow, RechargePackageItem } from '~/types/business-api'
import { requireLogin } from '~/utils/authLoginNavigation'
import { rechargeOrderList, rechargePackageList } from '~/utils/business/recharge'
import './billing.css'

const copy = {
  en: { title: 'Credits', packs: 'Credit packs', buy: 'Buy credits', unavailable: 'Payments unavailable', loading: 'Loading…', error: 'Unable to load', retry: 'Retry', signIn: 'Sign in', emptyPacks: 'No credit packs available', orders: 'Orders', emptyOrders: 'No orders yet', previous: 'Previous', next: 'Next', pricing: 'Model pricing', credits: 'credits', expires: 'Expires', pending: 'Awaiting payment', paid: 'Paid', failed: 'Failed', closed: 'Cancelled', refunded: 'Refunded' },
  zh: { title: '积分', packs: '积分套餐', buy: '购买积分', unavailable: '支付暂未开放', loading: '加载中…', error: '加载失败', retry: '重试', signIn: '登录', emptyPacks: '暂无积分套餐', orders: '订单', emptyOrders: '暂无订单', previous: '上一页', next: '下一页', pricing: '模型价格', credits: '积分', expires: '到期时间', pending: '待支付', paid: '已支付', failed: '支付失败', closed: '已取消', refunded: '已退款' },
  'zh-hant': { title: '積分', packs: '積分套餐', buy: '購買積分', unavailable: '付款暫未開放', loading: '載入中…', error: '載入失敗', retry: '重試', signIn: '登入', emptyPacks: '暫無積分套餐', orders: '訂單', emptyOrders: '暫無訂單', previous: '上一頁', next: '下一頁', pricing: '模型價格', credits: '積分', expires: '到期時間', pending: '待付款', paid: '已付款', failed: '付款失敗', closed: '已取消', refunded: '已退款' }
}
const labels = (locale: CreatorLocale) => copy[locale === 'zh' || locale === 'zh-hant' ? locale : 'en']

function OrderHistory({ locale }: { locale: CreatorLocale }) {
  const t = labels(locale)
  const [page, setPage] = useState(1)
  const [result, setResult] = useState<{ rows: RechargeOrderRow[]; total: number } | null>(null)
  const [failed, setFailed] = useState(false)
  const [retry, setRetry] = useState(0)
  const pageSize = 8
  const price = new Intl.NumberFormat(creatorLanguage(locale).tag, { style: 'currency', currency: 'CNY' })

  useEffect(() => {
    let active = true
    setResult(null)
    setFailed(false)
    void rechargeOrderList({ pageNum: page, pageSize }).then(data => {
      if (active) setResult(data)
    }).catch(() => { if (active) setFailed(true) })
    return () => { active = false }
  }, [page, retry])

  return <section className="credit-orders">
    <h2>{t.orders}</h2>
    {failed ? <p role="alert">{t.error} <button type="button" onClick={() => setRetry(retry + 1)}>{t.retry}</button></p> :
      !result ? <p role="status">{t.loading}</p> :
      !result.rows.length ? <p>{t.emptyOrders}</p> :
      <>
        <ul>{result.rows.map(order => <li key={order.orderNo}>
          <span>{order.productName}<small>{order.orderNo}</small></span>
          <span>{price.format(order.payPrice)}</span>
          <span>{['pending', 'paid', 'failed', 'closed', 'refunded'].includes(order.payStatus) ? t[order.payStatus as 'pending' | 'paid' | 'failed' | 'closed' | 'refunded'] : order.payStatus}</span>
        </li>)}</ul>
        {result.total > pageSize && <div className="credit-pagination">
          <button type="button" disabled={page <= 1} onClick={() => setPage(page - 1)}>{t.previous}</button>
          <span>{page} / {Math.ceil(result.total / pageSize)}</span>
          <button type="button" disabled={page * pageSize >= result.total} onClick={() => setPage(page + 1)}>{t.next}</button>
        </div>}
      </>}
  </section>
}

function CreditPacks({ locale }: { locale: CreatorLocale }) {
  const t = labels(locale)
  const { anyPaymentEnabled, loaded } = useAuthPublicConfig()
  const [rows, setRows] = useState<RechargePackageItem[] | null>(null)
  const [failed, setFailed] = useState(false)
  const [attempt, setAttempt] = useState(0)
  const tag = creatorLanguage(locale).tag
  const price = new Intl.NumberFormat(tag, { style: 'currency', currency: 'CNY' })
  const number = new Intl.NumberFormat(tag)

  useEffect(() => {
    let active = true
    setFailed(false)
    setRows(null)
    void rechargePackageList().then(data => { if (active) setRows(data) }).catch(() => { if (active) setFailed(true) })
    return () => { active = false }
  }, [attempt])

  return <section className="credit-packs" aria-labelledby="credit-packs-title">
    <div className="credit-section-heading"><h2 id="credit-packs-title">{t.packs}</h2>
      <button type="button" disabled={!loaded || !anyPaymentEnabled || !rows?.length} onClick={() => window.dispatchEvent(new Event('open-recharge-modal'))}>
        {!loaded ? t.loading : anyPaymentEnabled ? t.buy : t.unavailable}
      </button>
    </div>
    {failed ? <p role="alert">{t.error} <button type="button" onClick={() => setAttempt(x => x + 1)}>{t.retry}</button></p> :
      !rows ? <p role="status">{t.loading}</p> :
      !rows.length ? <p>{t.emptyPacks}</p> : <div className="credit-packs-grid">{rows.map(pack => <article key={pack.id}>
        <h3>{pack.packageName}</h3>
        <p className="credit-pack-amount">{number.format(pack.credits)} <small>{t.credits}</small></p>
        <p className="credit-pack-price">{price.format(pack.payPrice)}{pack.originalPrice > pack.payPrice && <del>{price.format(pack.originalPrice)}</del>}</p>
      </article>)}</div>}
  </section>
}

export default function BillingPage() {
  const token = useUserStore(state => state.token)
  const profile = useUserStore(state => state.user)
  const { locale } = useCreatorLocale()
  const t = labels(locale)
  useEffect(() => { if (token) void useUserStore.getState().fetchProfile() }, [token])

  return <main className="credit-workspace creator-credit-page" lang={creatorLanguage(locale).tag} dir={locale === 'ar' ? 'rtl' : 'ltr'}>
    <div className="credit-account">
      <header><div><h1>{t.title}</h1>
        {token && profile?.memberLevelName && <p>{profile.memberLevelName}{profile.memberExpireTime && <> · {t.expires} {profile.memberExpireTime}</>}</p>}
      </div>
        {!token && <button type="button" onClick={() => requireLogin({ redirect: '/billing/' })}>{t.signIn}</button>}
      </header>
      {token && <>
        <CreditPacks key={token} locale={locale} />
        <LandingBilling key={`${token}:${profile?.balance ?? ''}`} locale={locale} />
        <OrderHistory key={token} locale={locale} />
      </>}
    </div>
    <details className="credit-model-pricing"><summary>{t.pricing}</summary><BillingPanel /></details>
  </main>
}
