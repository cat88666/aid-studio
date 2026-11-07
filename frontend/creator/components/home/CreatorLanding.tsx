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
import { sampleLibrary, sampleCopy, sampleSource } from '~/utils/sampleLibrary'
import sampleLabels from '~/config/sampleLibraryLabels.json'
import { LandingHeroMedia } from './LandingHeroMedia'
import { CreatorLandingDetails } from './CreatorLandingDetails'
import './CreatorLanding.css'

export default function CreatorLanding() {
  const { locale } = useCreatorLocale()
  const t = homeCopy[locale]
  const router = useRouter()
  const [selected, setSelected] = useState<string | null>(null)
  const [category, setCategory] = useState<'all' | 'films' | 'models'>('all')
  const labels = sampleLabels[locale]
  const visibleSamples = sampleLibrary.filter(sample => category === 'all' || sample.category === category)
  const activeSample = sampleLibrary.find(sample => sample.id === selected)
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
      <section className="showcase-samples" id="samples" aria-labelledby="showcase-samples-title">
        <div className="showcase-section-heading">
          <div><h2 id="showcase-samples-title">{t.sampleTitle}</h2></div>
        </div>
        <div className="showcase-sample-toolbar">
          <div role="group" aria-label={t.samples} className="showcase-sample-filters">
            {(['all', 'films', 'models'] as const).map(key => <button type="button" key={key} aria-pressed={category === key} onClick={() => { setCategory(key); setHovered(null) }}>{labels[key]}<span>{key === 'all' ? sampleLibrary.length : sampleLibrary.filter(sample => sample.category === key).length}</span></button>)}
          </div>
          <span>{visibleSamples.length} {labels.count}</span>
        </div>
        <div className="showcase-sample-grid" data-category={category}>
          {visibleSamples.map((sample) => <button key={sample.id} type="button" className="showcase-sample" aria-label={`${t.watch} · ${sampleCopy(sample.id, locale)[0]}`}
            onClick={() => { setPlayError(false); setHovered(null); setSelected(sample.id) }}
            onPointerEnter={event => {
              if (event.pointerType === 'mouse' && !window.matchMedia('(prefers-reduced-motion: reduce)').matches) setHovered(sample.id)
            }} onPointerLeave={() => setHovered(null)}>
            <img src={withAppBasePath(sample.poster)} alt="" loading="lazy" />
            {hovered === sample.id && <video src={withAppBasePath(sample.video)} autoPlay muted playsInline loop aria-hidden="true" onError={() => setHovered(null)} />}
            <span className="showcase-sample-play"><CaretRightFilled /></span>
            <span className="showcase-sample-caption"><strong>{sampleCopy(sample.id, locale)[0]}</strong><span>{sampleCopy(sample.id, locale)[1]}</span></span>
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

    <Modal rootClassName="creator-showcase-modal" open={selected !== null} onCancel={() => setSelected(null)} footer={null} width={1000} centered destroyOnHidden title={activeSample ? sampleCopy(activeSample.id, locale)[0] : t.player}>
      {activeSample && <div className="showcase-player">
        <video key={activeSample.id} src={withAppBasePath(activeSample.video)} poster={withAppBasePath(activeSample.poster)} controls autoPlay playsInline onError={() => setPlayError(true)} />
        <p role={playError ? 'alert' : undefined}>{playError ? t.videoError : sampleCopy(activeSample.id, locale)[1]}</p>
        {activeSample.category === 'models' && <p className="showcase-source">{labels.source}: <a href={sampleSource.url} target="_blank" rel="noreferrer">{sampleSource.name}</a> · <a href={sampleSource.licenseUrl} target="_blank" rel="noreferrer">{sampleSource.license}</a><br />{labels.adapted}</p>}
      </div>}
    </Modal>
  </main>
}
