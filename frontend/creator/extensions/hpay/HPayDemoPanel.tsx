'use client'

import { useEffect, useRef, useState } from 'react'
import type { CreatorLocale } from '~/config/creatorLanguages'

// Presentation-only fixtures. Never sent to the real payment or account APIs.
const demoPacks = [
  { id: 'demo-starter', credits: 100, amount: '1.00' },
  { id: 'demo-standard', credits: 1000, amount: '10.00' },
  { id: 'demo-pro', credits: 5000, amount: '50.00' }
] as const
const copy = {
  en: {
    title: 'USDT payments', badge: 'Demo mode', notice: 'Try the payment flow. Demo prices only — no real payment or credits are involved.',
    pack: 'Demo credit pack', credits: 'credits', pay: 'Simulate payment', checkout: 'Demo checkout', merchant: 'Merchant: Demo store',
    noTransfer: 'Simulation only. Do not transfer funds. There is no real wallet address or payment QR code.',
    success: 'Simulate success', failure: 'Simulate failure', done: 'Simulation successful', failed: 'Simulated payment failed',
    unchanged: 'No funds were charged and your real credit balance has not changed.', retry: 'Try again', close: 'Close'
  },
  zh: {
    title: 'USDT 充值', badge: '模拟模式', notice: '先体验完整支付流程。下方为演示价格，不产生真实付款或积分。',
    pack: '演示积分套餐', credits: '积分', pay: '模拟支付', checkout: '模拟收银台', merchant: '商户：演示商户',
    noTransfer: '仅供模拟，请勿转账。此页面没有真实钱包地址或付款二维码。',
    success: '模拟支付成功', failure: '模拟支付失败', done: '模拟支付成功', failed: '模拟支付失败',
    unchanged: '未发生真实扣款，您的实际积分余额没有变化。', retry: '重新模拟', close: '关闭'
  }
}

export default function HPayDemoPanel({ locale }: { locale: CreatorLocale }) {
  const t = copy[locale === 'zh' || locale === 'zh-hant' ? 'zh' : 'en']
  const [selected, setSelected] = useState<(typeof demoPacks)[number] | null>(null)
  const [step, setStep] = useState<'checkout' | 'success' | 'failure'>('checkout')
  const dialog = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    if (selected && dialog.current && !dialog.current.open) dialog.current.showModal()
  }, [selected])
  const close = () => { dialog.current?.close(); setSelected(null) }

  return <section className="hpay-panel hpay-demo" aria-labelledby="hpay-demo-title">
    <h2 id="hpay-demo-title">{t.title} <span className="hpay-demo-badge">{t.badge}</span></h2>
    <p>{t.notice}</p>
    <div className="hpay-packs">{demoPacks.map(pack => <article key={pack.id}>
      <h3>{t.pack}</h3><p>{pack.credits} {t.credits}</p><strong>{pack.amount} USDT</strong>
      <div><button type="button" onClick={() => { setStep('checkout'); setSelected(pack) }}>{t.pay}</button></div>
    </article>)}</div>
    {selected && <dialog ref={dialog} className="hpay-demo-dialog" aria-labelledby="hpay-demo-checkout-title"
      onCancel={close} onClose={() => setSelected(null)} onClick={event => { if (event.target === event.currentTarget) close() }}>
      <div className="hpay-demo-checkout">
        <span className="hpay-demo-badge">{t.badge}</span>
        <h2 id="hpay-demo-checkout-title">{step === 'checkout' ? t.checkout : step === 'success' ? t.done : t.failed}</h2>
        <p>{t.merchant}</p>
        <strong className="hpay-demo-amount">{selected.amount} USDT</strong>
        <p>{selected.credits} {t.credits} · {t.pack}</p>
        <p role="status">{step === 'checkout' ? t.noTransfer : t.unchanged}</p>
        <div className="hpay-demo-actions">
          {step === 'checkout' ? <>
            <button type="button" onClick={() => setStep('success')}>{t.success}</button>
            <button type="button" onClick={() => setStep('failure')}>{t.failure}</button>
          </> : <button type="button" onClick={() => setStep('checkout')}>{t.retry}</button>}
          <button type="button" onClick={close}>{t.close}</button>
        </div>
      </div>
    </dialog>}
  </section>
}
