'use client'

import Link from 'next/link'
import { Modal } from 'antd'
import { ArrowDownOutlined, ArrowRightOutlined, CaretRightFilled } from '@ant-design/icons'
import { useRouter } from 'next/navigation'
import { useState } from 'react'
import { creatorLanguage } from '~/config/creatorLanguages'
import { homeCopy } from '~/config/home-locales'
import { creatorBrandName } from '~/config/creatorBrand'
import { rememberCreatorLocale, useCreatorLocale } from '~/hooks/useCreatorLocale'
import { withAppBasePath } from '~/utils/appBasePath'
import { landingSamples } from '~/utils/landingSamples'
import { LandingHeroMedia } from './LandingHeroMedia'
import { CreatorLandingDetails } from './CreatorLandingDetails'
import './CreatorLanding.css'

export default function CreatorLanding() {
  const { locale } = useCreatorLocale()
  const t = homeCopy[locale]
  const router = useRouter()
  const [selected, setSelected] = useState<number | null>(null)
  const [hovered, setHovered] = useState<string | null>(null)
  const [playError, setPlayError] = useState(false)

  function startCreating() {
    rememberCreatorLocale(locale)
    router.push('/start/')
  }

  return <main className="creator-showcase">
    <section className="showcase-hero" aria-labelledby="showcase-title">
      <LandingHeroMedia playLabel={t.watch} pauseLabel={t.pause} />
      <div className="showcase-hero-copy">
        <p className="showcase-eyebrow">{t.kicker}</p>
        <h1 id="showcase-title">{t.headline}</h1>
        <p className="showcase-intro">{t.intro}</p>
        <div className="showcase-hero-actions">
          <button type="button" onClick={startCreating}>{t.create}<ArrowRightOutlined /></button>
          <a href="#samples"><CaretRightFilled />{t.watch}</a>
        </div>
        <p className="showcase-hero-note">{t.note}</p>
        <a href="#workflow" className="showcase-hero-guide"><span>{t.formats}</span><span>{t.guide}<ArrowDownOutlined /></span></a>
      </div>
    </section>

    <div className="showcase-content">
      <section className="showcase-models" aria-label={t.models}>
        <p>{t.models}</p>
        <div>{['MiniMax', 'Vidu', 'Seedance', 'DeepSeek', 'Seedream', 'Qwen'].map(model => <span key={model}>{model}</span>)}</div>
      </section>

      <section className="showcase-samples" id="samples" aria-labelledby="showcase-samples-title">
        <div className="showcase-section-heading">
          <div><p className="showcase-eyebrow">{t.samples}</p><h2 id="showcase-samples-title">{t.sampleTitle}</h2><p>{t.sampleLead}</p></div>
          <Link href="/gallery/" onClick={() => rememberCreatorLocale(locale)}>{t.gallery}<ArrowRightOutlined /></Link>
        </div>
        <div className="showcase-sample-grid">
          {landingSamples.map((sample, index) => <button key={sample.id} type="button" className="showcase-sample" aria-label={`${t.watch} · ${t.films[index][0]}`}
            onClick={() => { setPlayError(false); setHovered(null); setSelected(index) }}
            onPointerEnter={event => {
              if (event.pointerType === 'mouse' && !window.matchMedia('(prefers-reduced-motion: reduce)').matches) setHovered(sample.id)
            }} onPointerLeave={() => setHovered(null)}>
            <img src={withAppBasePath(sample.poster)} alt="" loading="lazy" />
            {hovered === sample.id && <video src={withAppBasePath(sample.video)} autoPlay muted playsInline loop aria-hidden="true" onError={() => setHovered(null)} />}
            <span className="showcase-sample-play"><CaretRightFilled /></span>
            <span className="showcase-sample-caption"><strong>{t.films[index][0]}</strong><span>{t.films[index][1]}</span></span>
          </button>)}
        </div>
      </section>

      <CreatorLandingDetails locale={locale} onCreate={startCreating} />
    </div>

    <footer className="showcase-footer"><div>
      <Link href={creatorLanguage(locale).path} className="showcase-footer-brand">{creatorBrandName(locale)}</Link>
      <nav aria-label={t.nav}>
        <a href="#samples">{t.examples}</a><a href="#pricing">{t.pricing}</a>
        <Link href="/guide/" onClick={() => rememberCreatorLocale(locale)}>{t.guide}</Link>
        <Link href="/faq/" onClick={() => rememberCreatorLocale(locale)}>{t.helpCenter}</Link>
      </nav><span>© {new Date().getFullYear()} {creatorBrandName(locale)}</span>
    </div></footer>

    <Modal open={selected !== null} onCancel={() => setSelected(null)} footer={null} width={1000} centered destroyOnHidden title={selected === null ? t.player : t.films[selected][0]}>
      {selected !== null && <div className="showcase-player">
        <video key={landingSamples[selected].id} src={withAppBasePath(landingSamples[selected].video)} poster={withAppBasePath(landingSamples[selected].poster)} controls autoPlay playsInline onError={() => setPlayError(true)} />
        <p role={playError ? 'alert' : undefined}>{playError ? t.videoError : t.films[selected][1]}</p>
      </div>}
    </Modal>
  </main>
}
