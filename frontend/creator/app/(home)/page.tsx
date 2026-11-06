'use client'

import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect, useRef, useState } from 'react'
import { GlobalOutlined, MenuOutlined, PlayCircleOutlined, SoundOutlined, PauseCircleOutlined, CloseOutlined } from '@ant-design/icons'
import { useHomeShellCreateModal } from '~/composables/useHomeShellCreateModal'
import { useUserStore } from '~/stores/user'
import { withAppBasePath } from '~/utils/appBasePath'
import { requireLogin } from '~/utils/authLoginNavigation'
import { useCreatorLocale } from '~/hooks/useCreatorLocale'
import { landingSamples } from '~/utils/landingSamples'
import { creatorBrandName } from '~/config/creatorBrand'
import './creator-home.css'

const copy = {
  en: {
    home: 'Home', nav: 'Main navigation', menu: 'Toggle menu', account: 'My projects', login: 'Sign in',
    pricing: 'Pricing', guide: 'Creator guide', examples: 'Showcase',
    kicker: 'AI filmmaking studio', headline: 'Turn your story into a short film',
    intro: 'Write the script, cast your characters, and direct every shot in one studio.',
    create: 'Start creating', watch: 'Watch samples', formats: 'Short dramas · Films · Motion comics',
    note: 'Start with a script. Shoot in landscape or portrait.', models: 'Model catalog',
    samples: 'Samples', sampleTitle: 'Stories in motion', sampleLead: 'Explore six clips and see how art direction and camera movement shape a scene.',
    gallery: 'Browse public projects', workflow: 'Workflow', workflowTitle: 'Make your first episode in three steps',
    stages: [
      ['Step one', 'Write the script', 'Outline the story, give your characters dialogue, and break the action into scenes.'],
      ['Step two', 'Plan each shot', 'Choose character and location references, then set the framing, action, and dialogue for each shot.'],
      ['Step three', 'Cut the episode', 'Generate clips, audition voices, and arrange your shots on the timeline before exporting.']
    ],
    dialogue: 'Dialogue', voiceTitle: 'Find a voice for your character', voiceLead: 'Listen to these Mandarin voice samples and choose a sound that fits your cast.',
    audition: 'Preview', pause: 'Pause', playing: 'Playing', voiceLanguage: 'Mandarin',
    usage: 'Pricing', pricingTitle: 'See the cost before you generate', pricingLead: 'Choose a model and settings to see your quote. Completed charges appear in your billing history.',
    prices: [['Images', 'Characters and locations'], ['Video', 'Shots and sequences'], ['Audio', 'Dialogue and voice-over']],
    bill: 'View my billing history', help: 'Help', faq: 'Frequently asked questions',
    questions: [
      ['Can I bring my own script?', 'Yes. Create a project, open the script editor, and add your story, characters, and dialogue before building the storyboard.'],
      ['Can I change a single shot?', 'Open the shot in the workspace, edit its description, reference image, or dialogue, then regenerate that shot.'],
      ['How do I choose a voice?', 'In the voice-over step, preview voices, enter your lines, and add the generated audio to the corresponding shot.'],
      ['Where can I download my film?', 'Check the shot order on the episode timeline and export it. You can download the video once the export finishes.']
    ],
    helpCenter: 'Visit the help center', player: 'Sample player', close: 'Close sample',
    audioError: 'This voice could not be played. Please try again.', videoError: 'This clip could not be played. Reopen it to try again.'
  },
  zh: {
    home: '首页', nav: '主导航', menu: '展开或收起导航', account: '我的作品', login: '登录 / 注册',
    pricing: '用量计费', guide: '创作指南', examples: '作品范例',
    kicker: 'AI 短剧与电影创作工具', headline: '把你的故事，拍成一部短剧',
    intro: '从剧本、角色到每一个镜头，在同一个片场完成。',
    create: '开始创作', watch: '看样片', formats: '短剧 · 电影 · 动态漫',
    note: '支持横屏和竖屏，从一段剧本开始', models: '模型目录',
    samples: '样片', sampleTitle: '看看镜头里的故事', sampleLead: '从画面风格到镜头运动，点开片段，看看不同的拍法。',
    gallery: '浏览公开作品', workflow: '流程', workflowTitle: '三步，完成第一集短剧',
    stages: [
      ['第一步', '写下剧本', '从一段故事开始，写清人物、地点和对白，把故事分成场次。'],
      ['第二步', '确定角色与分镜', '选定人物造型和场景，为每个镜头安排构图、动作和台词。'],
      ['第三步', '剪成一集', '逐镜生成视频，挑选配音，在时间线上排好片段并导出成片。']
    ],
    dialogue: '对白', voiceTitle: '听见角色的声音', voiceLead: '试听音色库里的普通话声音，为你的角色挑一位演员。',
    audition: '试听', pause: '暂停', playing: '正在播放', voiceLanguage: '普通话对白',
    usage: '用量', pricingTitle: '生成之前，先看用量', pricingLead: '在创作工作台选择模型和参数后查看本次报价，已完成的消费可在账单中查询。',
    prices: [['图像', '角色与场景'], ['视频', '分镜与片段'], ['声音', '台词与配音']],
    bill: '查看我的账单', help: '帮助', faq: '常见问题',
    questions: [
      ['可以用自己的剧本吗？', '可以。创建作品后进入剧本创作，填写自己的故事、角色与对白，再继续拆分分镜。'],
      ['可以只修改一个镜头吗？', '在工作台中打开对应分镜，调整描述、参考图或台词，再为这个分镜重新生成。'],
      ['如何给角色选择声音？', '进入配音步骤，试听音色、填写台词，并把生成的语音放到对应镜头。'],
      ['在哪里下载成片？', '在剧集时间线上确认镜头顺序，提交导出。任务完成后即可下载视频。']
    ],
    helpCenter: '前往帮助中心', player: '样片播放', close: '关闭样片',
    audioError: '音色暂时无法播放，请重新试听。', videoError: '样片暂时无法播放，请重新打开。'
  }
}

const voices = [
  { en: 'Vivi', zh: 'Vivi', avatar: 'Vivi 2.0', file: '5e4fb1b33f494a9d8dfdd86dd07abbd8' },
  { en: 'Xiaotian', zh: '小天', avatar: '小天 2.0', file: '8a02c38a64244725a363b1067cb45604' },
  { en: 'Sweet Peach', zh: '甜美桃子', avatar: '甜美桃子 2.0', file: '5324a8e760334d43b6db544ae15e7703' },
  { en: 'Easygoing Youth', zh: '反卷青年', avatar: '反卷青年 2.0', file: '3c16ec808a234053a14dbe3500e41ae3' }
]

export default function CreatorHomePage() {
  const { locale, setLocale } = useCreatorLocale()
  const t = copy[locale]
  const navigation = [[t.pricing, '#pricing'], [t.guide, '#guide'], [t.examples, '#samples']]
  const router = useRouter()
  const token = useUserStore((state) => state.token)
  const createModal = useHomeShellCreateModal()
  const [menuOpen, setMenuOpen] = useState(false)
  const [sample, setSample] = useState<(typeof landingSamples)[number] | null>(null)
  const [voice, setVoice] = useState('')
  const [mediaError, setMediaError] = useState<'audioError' | 'videoError' | ''>('')
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
    <div className="creator-landing" lang={locale === 'en' ? 'en' : 'zh-CN'}>
      <header className="creator-nav">
        <div className="creator-container">
          <Link href="/" aria-label={creatorBrandName(locale) + ' ' + t.home} className="creator-brand">
            {creatorBrandName(locale)}
          </Link>
          <nav className={menuOpen ? 'creator-links is-open' : 'creator-links'} aria-label={t.nav}>
            {navigation.map(([label, href]) => <a key={href} href={href} onClick={() => setMenuOpen(false)}>{label}</a>)}
          </nav>
          <label className="creator-language">
            <GlobalOutlined aria-hidden="true" />
            <select aria-label="Language / 语言" value={locale} onChange={event => setLocale(event.target.value === 'zh' ? 'zh' : 'en')}>
              <option value="en" lang="en">EN</option>
              <option value="zh" lang="zh-CN">中文</option>
            </select>
          </label>
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
            {landingSamples.map(item => (
              <button type="button" className="creator-film" key={item.video} onClick={() => {
                audio.current?.pause()
                setVoice('')
                setMediaError('')
                setSample(item)
              }}>
                <img loading="lazy" src={withAppBasePath(item.poster)} alt="" />
                <span className="creator-film-caption"><strong>{item.title[locale]}</strong><small>{item.subtitle[locale]}</small></span>
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
            {voices.map(({ en, zh, avatar, file }) => <button type="button" key={file} aria-label={(voice === file ? t.pause : t.audition) + ' ' + (locale === 'en' ? en : zh)} onClick={() => playVoice(file)}>
              <img loading="lazy" src={withAppBasePath('/profile/aid/voice/avatar/2026/05/22/' + avatar + '.jpg')} alt="" />
              <span><b>{locale === 'en' ? en : zh}</b><small>{voice === file ? t.playing : t.voiceLanguage}</small></span>
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

      <dialog className="creator-player" ref={player} aria-label={sample?.title[locale] || t.player} onClose={() => { setSample(null); setMediaError('') }} onClick={event => {
        if (event.target === event.currentTarget) player.current?.close()
      }}>
        {sample && <div>
          <header><strong>{sample.title[locale]}</strong><button type="button" aria-label={t.close} onClick={() => player.current?.close()}><CloseOutlined /></button></header>
          <video key={sample.video} src={withAppBasePath(sample.video)} poster={withAppBasePath(sample.poster)} controls autoPlay playsInline onError={() => setMediaError('videoError')} />
          <p>{mediaError ? t[mediaError] : sample.subtitle[locale]}</p>
        </div>}
      </dialog>
    </div>
  )
}
