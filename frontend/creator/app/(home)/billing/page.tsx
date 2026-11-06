'use client'

import { useEffect, useState } from 'react'
import BillingPanel from '~/components/home/BillingPanel'
import { LandingBilling } from '~/components/home/LandingBilling'
import { formatPayStatus, formatPrice } from '~/components/common/recharge/rechargeFormat'
import { useAuthPublicConfig } from '~/hooks/useAuthPublicConfig'
import { useCreatorLocale } from '~/hooks/useCreatorLocale'
import { useUserStore } from '~/stores/user'
import type { RechargeOrderRow } from '~/types/business-api'
import { requireLogin } from '~/utils/authLoginNavigation'
import { rechargeOrderList } from '~/utils/business/recharge'

function OrderHistory() {
  const [page, setPage] = useState(1)
  const [result, setResult] = useState<{ rows: RechargeOrderRow[]; total: number } | null>(null)
  const [failed, setFailed] = useState(false)
  const [retry, setRetry] = useState(0)
  const pageSize = 8

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
    <h2>订单记录</h2>
    {failed ? <p role="alert">加载失败 <button type="button" onClick={() => setRetry(retry + 1)}>重试</button></p> :
      !result ? <p role="status">加载中…</p> :
      !result.rows.length ? <p>暂无订单</p> :
      <>
        <ul>{result.rows.map(order => <li key={order.orderNo}>
          <span>{order.productName}<small>{order.orderNo}</small></span>
          <span>¥ {formatPrice(order.payPrice)}</span>
          <span>{formatPayStatus(order.payStatus)}</span>
        </li>)}</ul>
        {result.total > pageSize && <div className="credit-pagination">
          <button type="button" disabled={page <= 1} onClick={() => setPage(page - 1)}>上一页</button>
          <span>{page} / {Math.ceil(result.total / pageSize)}</span>
          <button type="button" disabled={page * pageSize >= result.total} onClick={() => setPage(page + 1)}>下一页</button>
        </div>}
      </>}
  </section>
}

export default function BillingPage() {
  const token = useUserStore(state => state.token)
  const { locale } = useCreatorLocale()
  const { anyPaymentEnabled } = useAuthPublicConfig()

  return <div className="credit-workspace">
    <div className="credit-account">
      <header><h1>积分</h1>
        {token ? (anyPaymentEnabled && <button type="button" onClick={() => window.dispatchEvent(new Event('open-recharge-modal'))}>购买积分</button>) :
          <button type="button" onClick={() => requireLogin({ redirect: '/billing' })}>登录查看余额</button>}
      </header>
      {token && <>
        <LandingBilling locale={locale} />
        <OrderHistory key={token} />
      </>}
    </div>
    <BillingPanel />
  </div>
}
