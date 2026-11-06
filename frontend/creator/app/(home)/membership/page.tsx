'use client'

import Link from 'next/link'
import { useEffect, useState } from 'react'
import { useCreatorLocale } from '~/hooks/useCreatorLocale'
import { useAuthPublicConfig } from '~/hooks/useAuthPublicConfig'
import { useUserStore } from '~/stores/user'
import type { RechargePackageItem } from '~/types/business-api'
import { rechargePackageList } from '~/utils/business/recharge'
import { requireLogin } from '~/utils/authLoginNavigation'
import { homeCopy } from '~/config/home-locales'
import { creatorLanguage } from '~/config/creatorLanguages'
import billing from '~/components/home/landingBillingText.json'
import labels from './labels.json'
import './membership.css'

export default function MembershipPage() {
  const { locale } = useCreatorLocale()
  const [title, packs, single, unavailable, empty, recharge, expires] = labels[locale]
  const text = billing[locale]
  const t = homeCopy[locale]
  const tag = creatorLanguage(locale).tag
  const token = useUserStore(state => state.token)
  const profile = useUserStore(state => state.user)
  const { anyPaymentEnabled, loaded } = useAuthPublicConfig()
  const [attempt, setAttempt] = useState(0)
  const [result, setResult] = useState<{ owner: string; rows: RechargePackageItem[] } | null>(null)
  const [error, setError] = useState(false)
  const rows = result?.owner === token ? result.rows : null
  const balance = profile?.balance
  const number = new Intl.NumberFormat(tag)
  const price = new Intl.NumberFormat(tag, { style: 'currency', currency: 'CNY' })

  useEffect(() => {
    if (!token) return
    let active = true
    setError(false)
    void useUserStore.getState().fetchProfile()
    rechargePackageList().then(
      data => { if (active) setResult({ owner: token, rows: data }) },
      () => { if (active) setError(true) }
    )
    return () => { active = false }
  }, [token, attempt])

  return (
    <main className="creator-membership" lang={tag} dir={locale === 'ar' ? 'rtl' : 'ltr'}>
      <header className="creator-membership__banner">
        <div>
          <h1>{title}</h1>
          {token && profile?.memberLevelName && <p>{profile.memberLevelName}</p>}
          {token && profile?.memberExpireTime && <p>{expires} {profile.memberExpireTime}</p>}
        </div>
        {token && balance != null && <div className="creator-membership__balance"><strong>✦ {number.format(balance)}</strong><span>{text.balance}</span></div>}
      </header>
      <section aria-labelledby="membership-pack-title">
        <div className="creator-membership__heading">
          <h2 id="membership-pack-title">{packs}</h2>
          {token && <Link href="/billing">{text.activity} →</Link>}
        </div>
        {!token ? (
          <button type="button" onClick={() => requireLogin({ redirect: '/membership' })}>{t.login}</button>
        ) : error ? (
          <p role="alert">{text.error} <button type="button" onClick={() => setAttempt(x => x + 1)}>{text.retry}</button></p>
        ) : !rows ? (
          <p role="status">{text.loading}</p>
        ) : !rows.length ? (
          <p role="status">{empty}{loaded && !anyPaymentEnabled && <> · {unavailable}</>}</p>
        ) : (
          <>
            <div className="creator-membership__plans">
              {rows.map(pack => (
                <article key={pack.id}>
                  <h3>{pack.packageName}</h3>
                  <p className="creator-membership__credits">✦ {number.format(pack.credits)} <small>{text.credits}</small></p>
                  {pack.originalPrice > pack.payPrice && <del>{price.format(pack.originalPrice)}</del>}
                  <p className="creator-membership__price">{price.format(pack.payPrice)}</p>
                  <p>{single}</p>
                  {pack.description && <p>{pack.description}</p>}
                </article>
              ))}
            </div>
            <footer>
              <button type="button" disabled={!anyPaymentEnabled} onClick={() => window.dispatchEvent(new Event('open-recharge-modal'))}>
                {loaded && !anyPaymentEnabled ? unavailable : recharge}
              </button>
            </footer>
          </>
        )}
      </section>
    </main>
  )
}
