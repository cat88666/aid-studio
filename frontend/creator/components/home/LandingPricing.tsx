'use client'

import dynamic from 'next/dynamic'
import { useEffect, useId, useState } from 'react'
import { creatorLanguage, type CreatorLocale } from '~/config/creatorLanguages'
import { homeCopy } from '~/config/home-locales'
import type { PublicBillingDetailData } from '~/types/business-api'
import { publicBillingDetail } from '~/utils/business/recharge'
import labels from './landingBillingText.json'

const LandingBilling = dynamic(() => import('./LandingBilling').then(module => module.LandingBilling))

const media = [
  { group: 'image', unit: 'IMAGE', price: 'unitPrice', quantity: 1 },
  { group: 'video', unit: 'SECOND', price: 'pricePerSecond', quantity: 1 },
  { group: 'voice', unit: 'CHAR', price: 'unitPrice', quantity: 1000 }
] as const

const units: Record<CreatorLocale, [string, string, string]> = {
  zh: ['张', '秒', '千字'], 'zh-hant': ['張', '秒', '千字'], en: ['image', 's', '1,000 chars'],
  ja: ['枚', '秒', '1,000文字'], ko: ['장', '초', '1,000자'], fr: ['image', 's', '1 000 car.'],
  de: ['Bild', 's', '1.000 Zeichen'], es: ['imagen', 's', '1.000 car.'], it: ['immagine', 's', '1.000 car.'],
  pt: ['imagem', 's', '1.000 car.'], id: ['gambar', 'dtk', '1.000 karakter'], ru: ['изобр.', 'с', '1 000 знаков'],
  tr: ['görsel', 'sn', '1.000 karakter'], nl: ['beeld', 's', '1.000 tekens'], uk: ['зобр.', 'с', '1 000 символів'],
  ar: ['صورة', 'ثانية', '١٬٠٠٠ حرف']
}

export function LandingPricing({ locale, onCreate }: { locale: CreatorLocale; onCreate: () => void }) {
  const t = homeCopy[locale]
  const id = useId()
  const [showBilling, setShowBilling] = useState(false)
  const [rates, setRates] = useState<PublicBillingDetailData | null>(null)
  const [failed, setFailed] = useState(false)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let active = true
    setFailed(false)
    publicBillingDetail().then(data => { if (active) setRates(data) })
      .catch(() => { if (active) setFailed(true) })
    return () => { active = false }
  }, [attempt])

  const format = new Intl.NumberFormat(creatorLanguage(locale).tag, { maximumFractionDigits: 8 })
  return <section id="pricing" className="creator-section creator-pricing">
    <div className="creator-pricing-layout">
      <div>
        <p className="creator-label">{t.pricing}</p>
        <h2>{t.pricingTitle}</h2>
        <p className="creator-lead">{t.pricingLead}</p>
        <div className="creator-pricing-actions">
          <button type="button" onClick={onCreate}>{t.create}</button>
          <button type="button" aria-expanded={showBilling} aria-controls={id} onClick={() => setShowBilling(!showBilling)}>{t.bill}</button>
        </div>
      </div>
      <div className="creator-rate-list" aria-live="polite" aria-busy={!rates && !failed}>
        {media.map(({ group, unit, price, quantity }, index) => {
          const models = rates?.[group] ?? []
          const model = models.find(item => item.rules.some(rule => rule.unit === unit && rule[price] != null))
          const rule = model?.rules.find(item => item.unit === unit && item[price] != null)
          const amount = model?.isFree ? 0 : rule?.[price]
          const [title, description] = t.prices[index]
          return <article key={group}>
            <div><h3>{title}</h3><p>{rule?.skuName || model?.modelName || description}</p></div>
            <div className="creator-rate-price">
              <strong>{amount != null ? (rule?.estimated && !model?.isFree ? '≈ ' : '') + format.format(amount * quantity) : '—'}</strong>
              {amount != null && <small>{rates?.creditUnit} / {units[locale][index]}</small>}
            </div>
          </article>
        })}
        {failed && <button type="button" className="creator-rate-retry" onClick={() => setAttempt(n => n + 1)}>{labels[locale].retry}</button>}
      </div>
    </div>
    <div id={id} hidden={!showBilling}>{showBilling && <LandingBilling locale={locale} />}</div>
  </section>
}
