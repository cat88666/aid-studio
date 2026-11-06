'use client'

import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect, useRef, useState } from 'react'
import { GlobalOutlined, MenuOutlined, PlayCircleOutlined, SoundOutlined, PauseCircleOutlined, CloseOutlined } from '@ant-design/icons'
import { useHomeShellCreateModal } from '~/composables/useHomeShellCreateModal'
import { useUserStore } from '~/stores/user'
import { withAppBasePath } from '~/utils/appBasePath'
import { requireLogin } from '~/utils/authLoginNavigation'
import { landingSamples } from '~/utils/landingSamples'
import './creator-home.css'

const navigation = [
  ['用量计费', '#pricing'], ['使用指南', '#guide'], ['作品范例', '#samples']
]

const stages = [
  ['第一步', '写下剧本', '从一段故事开始，写清人物、地点和对白，把故事分成场次。'],
  ['第二步', '确定角色与分镜', '选定人物造型和场景，为每个镜头安排构图、动作和台词。'],
  ['第三步', '剪成一集', '逐镜生成视频，挑选配音，在时间线上排好片段并导出成片。']
]

const voices = [
  ['Vivi', 'Vivi 2.0', '5e4fb1b33f494a9d8dfdd86dd07abbd8'],
  ['小天', '小天 2.0', '8a02c38a64244725a363b1067cb45604'],
  ['甜美桃子', '甜美桃子 2.0', '5324a8e760334d43b6db544ae15e7703'],
  ['反卷青年', '反卷青年 2.0', '3c16ec808a234053a14dbe3500e41ae3']
]

const questions = [
  ['可以用自己的剧本吗？', '可以。创建作品后进入剧本创作，填写自己的故事、角色与对白，再继续拆分分镜。'],
  ['可以只修改一个镜头吗？', '在工作台中打开对应分镜，调整描述、参考图或台词，再为这个分镜重新生成。'],
  ['如何给角色选择声音？', '进入配音步骤，试听音色、填写台词，并把生成的语音放到对应镜头。'],
  ['在哪里下载成片？', '在剧集时间线上确认镜头顺序，提交导出。任务完成后即可下载视频。']
]

export default function CreatorHomePage() {
  const router = useRouter()
  const token = useUserStore((state) => state.token)
  const createModal = useHomeShellCreateModal()
  const [menuOpen, setMenuOpen] = useState(false)
  const [sample, setSample] = useState<(typeof landingSamples)[number] | null>(null)
  const [voice, setVoice] = useState('')
  const [mediaError, setMediaError] = useState('')
  const player = useRef<HTMLDialogElement>(null)
  const audio = useRef<HTMLAudioElement>(null)

  useEffect(() => {
    if (sample) player.current?.showModal()
  }, [sample])

  function startCreating() {
    if (!token) return requireLogin({ redirect: '/works' })
    createModal.openCreateModal()
  }

  function openAccount(path: string) {
    if (!token) return requireLogin({ redirect: path })
    router.push(path)
  }

  function playVoice(name: string, file: string) {
    const element = audio.current
    if (!element) return
    if (voice === name) {
      element.pause()
      setVoice('')
      return
    }
    element.src = withAppBasePath('/profile/aid/voice/sample/2026/05/17/' + file + '.mp3')
    setMediaError('')
    setVoice(name)
    void element.play().catch(() => {
      setVoice('')
      setMediaError('音色暂时无法播放，请重新试听。')
    })
  }

  return (
    <div className="creator-landing">
      <header className="creator-nav">
        <div className="creator-container">
          <Link href="/" aria-label="AID Studio 首页" className="creator-brand">
            <svg width="32" height="32" viewBox="0 0 32 32" fill="none" aria-hidden="true">
              <path d="m5 23 9-16h5l8 16h-6l-4.4-9.5-5 9.5z" fill="currentColor"/>
              <path d="m13.5 24.5 3.6-6.3 3.2 6.3z" fill="#a98acb"/>
            </svg>
            <b>AID <span>STUDIO</span></b>
          </Link>
          <nav className={menuOpen ? 'creator-links is-open' : 'creator-links'} aria-label="主导航">
            {navigation.map(([label, href]) => <a key={href} href={href} onClick={() => setMenuOpen(false)}>{label}</a>)}
          </nav>
          <span className="creator-language"><GlobalOutlined /> 简体中文</span>
          <button type="button" className="creator-nav-account" onClick={() => openAccount('/works')}>{token ? '我的作品' : '登录 / 注册'}</button>
          <button type="button" className="creator-menu" aria-label="展开导航" aria-expanded={menuOpen} onClick={() => setMenuOpen(!menuOpen)}><MenuOutlined /></button>
        </div>
      </header>

      <main className="creator-container creator-content">
        <section className="creator-hero">
          <img className="creator-hero-image" src={withAppBasePath('/creator-home/hero.webp')} fetchPriority="high" alt="" width="1600" height="900"/>
          <div className="creator-hero-copy">
            <p className="creator-kicker">AI 短剧与电影创作工具</p>
            <h1>把你的故事，拍成一部短剧</h1>
            <p className="creator-hero-description">从剧本、角色到每一个镜头，在同一个片场完成。</p>
            <div className="creator-hero-actions">
              <button type="button" onClick={startCreating}>开始创作</button>
              <a href="#samples">看样片</a>
            </div>
            <span className="creator-hero-tag">短剧 · 电影 · 动态漫</span>
            <p className="creator-hero-note">支持横屏和竖屏，从一段剧本开始</p>
          </div>
        </section>

        <div className="creator-models" aria-label="模型目录">
          <span>模型目录</span>
          {['MiniMax', 'Seedance', 'DeepSeek', 'Vidu', 'Qwen', 'Seedream'].map(name => <b key={name}>{name}</b>)}
        </div>

        <section className="creator-section" id="samples">
          <p className="creator-label">样片</p>
          <h2>看看镜头里的故事</h2>
          <p className="creator-lead">从画面风格到镜头运动，点开片段，看看不同的拍法。</p>
          <div className="creator-films">
            {landingSamples.map(item => (
              <button type="button" className="creator-film" key={item.title} onClick={() => {
                audio.current?.pause()
                setVoice('')
                setMediaError('')
                setSample(item)
              }}>
                <img loading="lazy" src={withAppBasePath(item.poster)} alt="" />
                <span className="creator-film-caption"><strong>{item.title}</strong><small>{item.subtitle}</small></span>
                <PlayCircleOutlined className="creator-play" />
              </button>
            ))}
          </div>
          <Link className="creator-sub-link" href="/gallery">浏览公开作品 →</Link>
        </section>

        <section className="creator-section" id="guide">
          <p className="creator-label">流程</p>
          <h2>三步，完成第一集短剧</h2>
          <div className="creator-stages">
            {stages.map(([step, title, description]) => <article key={step}><small>{step}</small><h3>{title}</h3><p>{description}</p></article>)}
          </div>
        </section>

        <section className="creator-section" id="voices">
          <p className="creator-label">对白</p>
          <h2>听见角色的声音</h2>
          <p className="creator-lead">试听音色库里的中文声音，为你的角色挑一位演员。</p>
          <div className="creator-voices">
            {voices.map(([name, avatar, file]) => <button type="button" key={file} aria-label={(voice === name ? '暂停' : '试听') + name} onClick={() => playVoice(name, file)}>
              <img loading="lazy" src={withAppBasePath('/profile/aid/voice/avatar/2026/05/22/' + avatar + '.jpg')} alt="" />
              <span><b>{name}</b><small>{voice === name ? '正在播放' : '中文对白'}</small></span>
              {voice === name ? <PauseCircleOutlined /> : <SoundOutlined />}
            </button>)}
          </div>
          <audio ref={audio} onEnded={() => setVoice('')} />
          {!sample && mediaError && <p role="alert">{mediaError}</p>}
        </section>

        <section className="creator-section" id="pricing">
          <p className="creator-label">用量</p>
          <h2>生成之前，先看用量</h2>
          <p className="creator-lead">在创作工作台选择模型和参数后查看本次报价，已完成的消费可在账单中查询。</p>
          <div className="creator-prices">{[['图像', '角色与场景'], ['视频', '分镜与片段'], ['声音', '台词与配音']].map(([title,detail]) => <article key={title}><b>{title}</b><p>{detail}</p></article>)}</div>
          <button type="button" className="creator-sub-link" onClick={() => openAccount('/billing')}>查看我的账单 →</button>
        </section>

        <section className="creator-section" id="faq">
          <p className="creator-label">帮助</p><h2>常见问题</h2>
          <div className="creator-faq">{questions.map(([title, answer]) => <details key={title}><summary>{title}</summary><p>{answer}</p></details>)}</div>
          <Link href="/faq" className="creator-sub-link">前往帮助中心 →</Link>
        </section>
      </main>
      <footer className="creator-footer"><div className="creator-container"><b>AID Studio</b><a href="#samples">作品范例</a><a href="#guide">创作指南</a><a href="#faq">常见问题</a></div></footer>

      <dialog className="creator-player" ref={player} aria-label={sample?.title || '样片播放'} onClose={() => { setSample(null); setMediaError('') }} onClick={event => {
        if (event.target === event.currentTarget) player.current?.close()
      }}>
        {sample && <div>
          <header><strong>{sample.title}</strong><button type="button" aria-label="关闭样片" onClick={() => player.current?.close()}><CloseOutlined /></button></header>
          <video key={sample.video} src={withAppBasePath(sample.video)} poster={withAppBasePath(sample.poster)} controls autoPlay playsInline onError={() => setMediaError('样片暂时无法播放，请重新打开。')} />
          <p>{mediaError || sample.subtitle}</p>
        </div>}
      </dialog>
    </div>
  )
}
