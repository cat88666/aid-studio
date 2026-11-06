'use client'

import { useEffect, useState } from 'react'
import { creatorLanguage, type CreatorLocale } from '~/config/creatorLanguages'
import { useUserStore } from '~/stores/user'
import { requireLogin } from '~/utils/authLoginNavigation'
import { creditConsumeList } from '~/utils/business/recharge'
import { userBalance } from '~/utils/business/auth'
import type { CreditConsumeRecordRow, UserBalanceFromApi } from '~/types/business-api'
import labels from './landingBillingText.json'
import './LandingBilling.css'

const pageSize = 8

function openBillingLogin() {
  requireLogin({ redirect: null, leaveAuthRoute: false })
}

export function LandingBilling({ locale }: { locale: CreatorLocale }) {
  const token = useUserStore(state => state.token)
  useEffect(() => {
    if (token) return
    useUserStore.getState().hydrateFromStorage()
    if (!useUserStore.getState().token) openBillingLogin()
  }, [token])

  if (!token) {
    return <button type="button" className="landing-billing-action" onClick={openBillingLogin}>{labels[locale].signIn}</button>
  }
  return <AccountBilling key={token} locale={locale} />
}

function AccountBilling({ locale }: { locale: CreatorLocale }) {
  const t = labels[locale]
  const tag = creatorLanguage(locale).tag
  const [page, setPage] = useState(1)
  const [attempt, setAttempt] = useState(0)
  const [balance, setBalance] = useState<UserBalanceFromApi | null>(null)
  const [balanceFailed, setBalanceFailed] = useState(false)
  const [history, setHistory] = useState<{ rows: CreditConsumeRecordRow[]; total: number } | null>(null)
  const [historyFailed, setHistoryFailed] = useState(false)

  useEffect(() => {
    let active = true
    setBalanceFailed(false)
    void userBalance().then(value => {
      if (active) setBalance(value)
    }).catch(() => {
      if (active) setBalanceFailed(true)
    })
    return () => { active = false }
  }, [attempt])

  useEffect(() => {
    let active = true
    setHistory(null)
    setHistoryFailed(false)
    void creditConsumeList({ pageNum: page, pageSize }).then(result => {
      if (!active) return
      const lastPage = Math.max(1, Math.ceil(result.total / pageSize))
      if (page > lastPage) setPage(lastPage)
      else setHistory(result)
    }).catch(() => {
      if (active) setHistoryFailed(true)
    })
    return () => { active = false }
  }, [page, attempt])

  const formatAmount = new Intl.NumberFormat(tag, { maximumFractionDigits: 8 })
  const formatChange = new Intl.NumberFormat(tag, { maximumFractionDigits: 8, signDisplay: 'exceptZero' })
  const totalPages = Math.ceil((history?.total ?? 0) / pageSize)

  return (
    <section className="landing-billing" aria-label={t.activity}>
      {balance && <dl className="landing-billing-summary">
        <div><dt>{t.balance}</dt><dd>{formatAmount.format(balance.balance)}</dd></div>
        <div><dt>{t.frozen}</dt><dd>{formatAmount.format(balance.frozenBalance)}</dd></div>
      </dl>}
      <h3>{t.activity}</h3>
      {(balanceFailed || historyFailed) && <div className="landing-billing-feedback" role="alert">
        {t.error} <button type="button" onClick={() => setAttempt(attempt + 1)}>{t.retry}</button>
      </div>}
      {!history && !historyFailed && <p role="status">{t.loading}</p>}
      {history && (history.rows.length === 0 ? <p>{t.empty}</p> : <>
        <div className="landing-billing-table"><table>
          <thead><tr><th scope="col">{t.date}</th><th scope="col">{t.task}</th><th scope="col">{t.change} ({t.credits})</th></tr></thead>
          <tbody>{history.rows.map(row => <tr key={row.bizTraceId}>
            <td>{row.createTime || '—'}</td>
            <td><strong>{row.bizName || row.bizTypeName || row.bizType || '—'}</strong>{row.modelName && <small>{row.modelName}</small>}</td>
            <td><bdi>{formatChange.format(row.changeAmount)}</bdi></td>
          </tr>)}</tbody>
        </table></div>
        {totalPages > 1 && <div className="landing-billing-pages">
          <button type="button" disabled={page === 1} onClick={() => setPage(page - 1)}>{t.previous}</button>
          <span>{formatAmount.format(page)} / {formatAmount.format(totalPages)}</span>
          <button type="button" disabled={page >= totalPages} onClick={() => setPage(page + 1)}>{t.next}</button>
        </div>}
      </>)}
    </section>
  )
}
