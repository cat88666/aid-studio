'use client'

import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect, useRef, useState } from 'react'
import { GlobalOutlined, MenuOutlined, PlayCircleOutlined, SoundOutlined, PauseCircleOutlined, CloseOutlined } from '@ant-design/icons'
import { useHomeShellCreateModal } from '~/composables/useHomeShellCreateModal'
import { useUserStore } from '~/stores/user'
import { withAppBasePath } from '~/utils/appBasePath'
import { requireLogin } from '~/utils/authLoginNavigation'
import { homeCopy } from '~/config/home-locales'
import { creatorLanguage, creatorLanguages } from '~/config/creatorLanguages'
import { useCreatorLocale } from '~/hooks/useCreatorLocale'
import { landingSamples } from '~/utils/landingSamples'
import { creatorBrandName } from '~/config/creatorBrand'
import './creator-home.css'

const voices = [
  { avatar: 'Vivi 2.0', file: '5e4fb1b33f494a9d8dfdd86dd07abbd8' },
  { avatar: '小天 2.0', file: '8a02c38a64244725a363b1067cb45604' },
  { avatar: '甜美桃子 2.0', file: '5324a8e760334d43b6db544ae15e7703' },
  { avatar: '反卷青年 2.0', file: '3c16ec808a234053a14dbe3500e41ae3' }
]

export default function CreatorHomePage() {
  const { locale, setLocale } = useCreatorLocale()
  const t = homeCopy[locale]
  const language = creatorLanguage(locale)
  const languageMenu = useRef<HTMLDetailsElement>(null)
  const navigation = [[t.pricing, '#pricing'], [t.guide, '#guide'], [t.examples, '#samples']]
  const router = useRouter()
  const token = useUserStore((state) => state.token)
  const createModal = useHomeShellCreateModal()
  const [menuOpen, setMenuOpen] = useState(false)
  const [sampleIndex, setSampleIndex] = useState<number | null>(null)
  const sample = sampleIndex === null ? null : landingSamples[sampleIndex]
  const sampleText = sampleIndex === null ? null : t.films[sampleIndex]
  const [voice, setVoice] = useState('')
  const [mediaError, setMediaError] = useState<'audioError' | 'videoError' | ''>('')
  const player = useRef<HTMLDialogElement>(null)
  const audio = useRef<HTMLAudioElement>(null)

  useEffect(() => {
    if (sample) player.current?.showModal()
  }, [sample])

  useEffect(() => {
    const dismissOutside = (event: PointerEvent) => {
      const menu = languageMenu.current
      if (menu?.open && event.target instanceof Node && !menu.contains(event.target)) menu.open = false
    }
    const handleEscape = (event: KeyboardEvent) => {
      const menu = languageMenu.current
      if (event.key === 'Escape' && menu?.open) {
        menu.open = false
        menu.querySelector('summary')?.focus()
      }
    }
    document.addEventListener('pointerdown', dismissOutside)
    document.addEventListener('keydown', handleEscape)
    return () => {
      document.removeEventListener('pointerdown', dismissOutside)
      document.removeEventListener('keydown', handleEscape)
    }
  }, [])

  function startCreating() {
    if (!token) return requireLogin({ redirect: '/works' })
    createModal.openCreateModal()
  }

  function openAccount(path: string) {
    if (!token) return requireLogin({ redirect: path })
    router.push(path)
  }

  function playVoice(file: string) {
    const element = audio.current
    if (!element) return
    if (voice === file) {
      element.pause()
      setVoice('')
      return
    }
    element.src = withAppBasePath('/profile/aid/voice/sample/2026/05/17/' + file + '.mp3')
    setMediaError('')
    setVoice(file)
    void element.play().catch(() => {
      setVoice('')
      setMediaError('audioError')
    })
  }

  return (
    <div className="creator-landing" lang={language.tag} dir={locale === 'ar' ? 'rtl' : 'ltr'}>
      <header className="creator-nav">
        <div className="creator-container">
          <Link href={language.path} aria-label={creatorBrandName(locale) + ' ' + t.home} className="creator-brand">
            {creatorBrandName(locale)}
          </Link>
          <nav className={menuOpen ? 'creator-links is-open' : 'creator-links'} aria-label={t.nav}>
            {navigation.map(([label, href]) => <a key={href} href={href} onClick={() => setMenuOpen(false)}>{label}</a>)}
          </nav>
          <details className="creator-language" ref={languageMenu}>
            <summary aria-label={t.language + ' · ' + language.label}>
              <GlobalOutlined aria-hidden="true" /><span lang={language.tag}>{language.label}</span>
            </summary>
            <ul aria-label={t.language}>
              {creatorLanguages.map(entry => (
                <li key={entry.code}>
                  <a href={withAppBasePath(entry.path)} lang={entry.tag} hrefLang={entry.tag}
                    aria-current={entry.code === locale ? 'page' : undefined}
                    onClick={event => {
                      if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return
                      event.preventDefault()
                      if (languageMenu.current) languageMenu.current.open = false
                      setLocale(entry.code)
                    }}>
                    {entry.label}
                  </a>
                </li>
              ))}
            </ul>
          </details>
          <button type="button" className="creator-nav-account" onClick={() => openAccount('/works')}>{token ? t.account : t.login}</button>
          <button type="button" className="creator-menu" aria-label={t.menu} aria-expanded={menuOpen} onClick={() => setMenuOpen(!menuOpen)}><MenuOutlined /></button>
        </div>
      </header>

      <main className="creator-container creator-content">
        <section className="creator-hero">
          <img className="creator-hero-image" src={withAppBasePath('/creator-home/hero.webp')} fetchPriority="high" alt="" width="1600" height="900"/>
          <div className="creator-hero-copy">
            <p className="creator-kicker">{t.kicker}</p>
            <h1>{t.headline}</h1>
            <p className="creator-hero-description">{t.intro}</p>
            <div className="creator-hero-actions">
              <button type="button" onClick={startCreating}>{t.create}</button>
              <a href="#samples">{t.watch}</a>
            </div>
            <span className="creator-hero-tag">{t.formats}</span>
            <p className="creator-hero-note">{t.note}</p>
          </div>
        </section>

        <div className="creator-models" aria-label={t.models}>
          <span>{t.models}</span>
          {['MiniMax', 'Seedance', 'DeepSeek', 'Vidu', 'Qwen', 'Seedream'].map(name => <b key={name}>{name}</b>)}
        </div>

        <section className="creator-section" id="samples">
          <p className="creator-label">{t.samples}</p>
          <h2>{t.sampleTitle}</h2>
          <p className="creator-lead">{t.sampleLead}</p>
          <div className="creator-films">
            {landingSamples.map((item, index) => (
              <button type="button" className="creator-film" key={item.video} onClick={() => {
                audio.current?.pause()
                setVoice('')
                setMediaError('')
                setSampleIndex(index)
              }}>
                <img loading="lazy" src={withAppBasePath(item.poster)} alt="" />
                <span className="creator-film-caption"><strong>{t.films[index][0]}</strong><small>{t.films[index][1]}</small></span>
                <PlayCircleOutlined className="creator-play" />
              </button>
            ))}
          </div>
          <Link className="creator-sub-link" href="/gallery">{t.gallery} →</Link>
        </section>

        <section className="creator-section" id="guide">
          <p className="creator-label">{t.workflow}</p>
          <h2>{t.workflowTitle}</h2>
          <div className="creator-stages">
            {t.stages.map(([step, title, description]) => <article key={step}><small>{step}</small><h3>{title}</h3><p>{description}</p></article>)}
          </div>
        </section>

        <section className="creator-section" id="voices">
          <p className="creator-label">{t.dialogue}</p>
          <h2>{t.voiceTitle}</h2>
          <p className="creator-lead">{t.voiceLead}</p>
          <div className="creator-voices">
            {voices.map(({ avatar, file }, index) => <button type="button" key={file} aria-label={(voice === file ? t.pause : t.audition) + ' ' + t.voiceNames[index]} onClick={() => playVoice(file)}>
              <img loading="lazy" src={withAppBasePath('/profile/aid/voice/avatar/2026/05/22/' + avatar + '.jpg')} alt="" />
              <span><b>{t.voiceNames[index]}</b><small>{voice === file ? t.playing : t.voiceLanguage}</small></span>
              {voice === file ? <PauseCircleOutlined /> : <SoundOutlined />}
            </button>)}
          </div>
          <audio ref={audio} onEnded={() => setVoice('')} />
          {!sample && mediaError && <p role="alert">{t[mediaError]}</p>}
        </section>

        <section className="creator-section" id="pricing">
          <p className="creator-label">{t.usage}</p>
          <h2>{t.pricingTitle}</h2>
          <p className="creator-lead">{t.pricingLead}</p>
          <div className="creator-prices">{t.prices.map(([title,detail]) => <article key={title}><b>{title}</b><p>{detail}</p></article>)}</div>
          <button type="button" className="creator-sub-link" onClick={() => openAccount('/billing')}>{t.bill} →</button>
        </section>

        <section className="creator-section" id="faq">
          <p className="creator-label">{t.help}</p><h2>{t.faq}</h2>
          <div className="creator-faq">{t.questions.map(([title, answer]) => <details key={title}><summary>{title}</summary><p>{answer}</p></details>)}</div>
          <Link href="/faq" className="creator-sub-link">{t.helpCenter} →</Link>
        </section>
      </main>
      <footer className="creator-footer"><div className="creator-container"><b>{creatorBrandName(locale)}</b><a href="#samples">{t.examples}</a><a href="#guide">{t.guide}</a><a href="#faq">{t.faq}</a></div></footer>

      <dialog className="creator-player" ref={player} aria-label={sampleText?.[0] || t.player} onClose={() => { setSampleIndex(null); setMediaError('') }} onClick={event => {
        if (event.target === event.currentTarget) player.current?.close()
      }}>
        {sample && sampleText && <div>
          <header><strong>{sampleText[0]}</strong><button type="button" aria-label={t.close} onClick={() => player.current?.close()}><CloseOutlined /></button></header>
          <video key={sample.video} src={withAppBasePath(sample.video)} poster={withAppBasePath(sample.poster)} controls autoPlay playsInline onError={() => setMediaError('videoError')} />
          <p>{mediaError ? t[mediaError] : sampleText[1]}</p>
        </div>}
      </dialog>
    </div>
  )
}
