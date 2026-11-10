'use client'

import { useRef, useState } from 'react'
import { ArrowRightOutlined, PauseOutlined, PlayCircleOutlined, SoundOutlined } from '@ant-design/icons'
import type { CreatorLocale } from '~/config/creatorLanguages'
import { homeCopy } from '~/config/home-locales'
import faqExtra from '~/config/landingFaqExtra.json'
import { withAppBasePath } from '~/utils/appBasePath'
import { LandingPricing } from './LandingPricing'
import './CreatorLandingDetails.css'

const voices = [
  { avatar: 'Vivi 2.0', file: '5e4fb1b33f494a9d8dfdd86dd07abbd8' },
  { avatar: '小天 2.0', file: '8a02c38a64244725a363b1067cb45604' },
  { avatar: '甜美桃子 2.0', file: '5324a8e760334d43b6db544ae15e7703' },
  { avatar: '反卷青年 2.0', file: '3c16ec808a234053a14dbe3500e41ae3' }
]

const cameraFiles = ['变焦拉近', '变焦拉远', '环绕']
const cameraLabels: Record<CreatorLocale, [string, string, string]> = {
  zh: ['拉近', '拉远', '环绕'], 'zh-hant': ['拉近', '拉遠', '環繞'],
  en: ['Zoom in', 'Zoom out', 'Orbit'], ja: ['ズームイン', 'ズームアウト', '回り込み'],
  ko: ['확대', '축소', '회전'], fr: ['Zoom avant', 'Zoom arrière', 'Orbite'],
  de: ['Heranzoomen', 'Herauszoomen', 'Umkreisen'], it: ['Zoom avanti', 'Zoom indietro', 'Orbita'],
  es: ['Acercar', 'Alejar', 'Órbita'], pt: ['Aproximar', 'Afastar', 'Órbita'],
  id: ['Perbesar', 'Perkecil', 'Mengitari'], ru: ['Приближение', 'Отдаление', 'Облёт'],
  tr: ['Yakınlaştır', 'Uzaklaştır', 'Etrafında dön'], nl: ['Inzoomen', 'Uitzoomen', 'Rondom'],
  uk: ['Наближення', 'Віддалення', 'Обліт'], ar: ['تقريب', 'إبعاد', 'دوران']
}
const cameraCopy: Partial<Record<CreatorLocale, [string, string]>> = {
  zh: ['同一个画面，换一种讲法', '拉近情绪，拉远空间，让镜头绕着角色展开。选择一种运镜，观看短片示例。'],
  'zh-hant': ['同一個畫面，換一種講法', '拉近情緒，拉遠空間，讓鏡頭繞著角色展開。選擇一種運鏡，觀看短片範例。'],
  en: ['A different move. A different story.', 'Move closer to the emotion, reveal the setting, or circle the character. Pick a camera move to watch a short example.'],
  ja: ['カメラの動きで、物語が変わる', '表情に寄る、空間を見せる、人物を回り込む。カメラの動きを選び、短い映像で確かめましょう。']
}
const workflowImages = ['ink', 'neon', 'cinema']

export function CreatorLandingDetails({ locale, onCreate }: {
  locale: CreatorLocale
  onCreate: () => void
}) {
  const t = homeCopy[locale]
  const audio = useRef<HTMLAudioElement>(null)
  const [voice, setVoice] = useState(-1)
  const [playing, setPlaying] = useState(false)
  const [audioFailed, setAudioFailed] = useState(false)
  const [camera, setCamera] = useState(2)
  const [cameraStarted, setCameraStarted] = useState(false)
  const [videoFailed, setVideoFailed] = useState(false)
  const [cameraTitle, cameraDescription] = cameraCopy[locale] || [t.sampleTitle, t.sampleLead]

  function playVoice(index: number) {
    const element = audio.current
    if (!element) return
    setAudioFailed(false)
    if (voice === index && !element.paused) {
      element.pause()
      return
    }
    if (voice !== index) {
      setVoice(index)
      element.src = withAppBasePath('/profile/sakura/voice/sample/2026/05/17/' + voices[index].file + '.mp3')
    }
    void element.play().catch((error: unknown) => {
      if (error instanceof DOMException && error.name === 'AbortError') return
      setPlaying(false)
      setAudioFailed(true)
    })
  }

  return <div className="creator-landing-details" dir={locale === 'ar' ? 'rtl' : 'ltr'}>
    <section id="workflow" className="cld-section cld-workflow" aria-labelledby="cld-workflow-title">
      <header className="cld-section-heading"><p>{t.workflow}</p><h2 id="cld-workflow-title">{t.workflowTitle}</h2></header>
      <div className="cld-workflow-grid">
        {t.stages.map(([, title, description], index) => <article key={title}>
          <div className="cld-workflow-image"><img loading="lazy" src={withAppBasePath('/creator-home/' + workflowImages[index] + '.webp')} alt="" /></div>
          <h3>{title}</h3><p>{description}</p>
        </article>)}
      </div>
    </section>

    <section className="cld-section cld-camera" aria-labelledby="cld-camera-title">
      <div className="cld-camera-copy">
        <p className="cld-eyebrow">{t.samples}</p><h2 id="cld-camera-title">{cameraTitle}</h2><p>{cameraDescription}</p>
        <div className="cld-camera-choices" role="group" aria-label={t.samples}>
          {cameraLabels[locale].map((label, index) => <button key={index} type="button" aria-pressed={camera === index}
            onClick={() => { setCamera(index); setCameraStarted(true); setVideoFailed(false) }}>
            <PlayCircleOutlined aria-hidden="true" />{label}
          </button>)}
        </div>
      </div>
      <div className="cld-camera-player">
        {videoFailed ? <p role="alert">{t.videoError}</p> : <video key={camera} controls muted playsInline preload="none"
          autoPlay={cameraStarted} poster={withAppBasePath('/creator-home/meadow.webp')}
          aria-label={cameraLabels[locale][camera]}
          src={withAppBasePath('/profile/sakura/storyboard/img/2026/06/07/' + encodeURIComponent(cameraFiles[camera]) + '.mp4')}
          onError={() => setVideoFailed(true)} />}
      </div>
    </section>

    <section className="cld-section cld-voices" aria-labelledby="cld-voices-title">
      <header className="cld-section-heading"><p>{t.dialogue}</p><h2 id="cld-voices-title">{t.voiceTitle}</h2><span>{t.voiceLead}</span></header>
      <div className="cld-voice-grid">
        {voices.map((item, index) => <button type="button" key={item.file} aria-pressed={voice === index && playing}
          aria-label={(voice === index && playing ? t.pause : t.audition) + ' ' + t.voiceNames[index]} onClick={() => playVoice(index)}>
          <img loading="lazy" src={withAppBasePath('/profile/sakura/voice/avatar/2026/05/22/' + encodeURIComponent(item.avatar) + '.jpg')} alt="" />
          <span><strong>{t.voiceNames[index]}</strong><small>{voice === index && playing ? t.playing : t.voiceLanguage}</small></span>
          <span className="cld-voice-control">{voice === index && playing ? <PauseOutlined /> : <SoundOutlined />}</span>
        </button>)}
      </div>
      <audio ref={audio} preload="none" onPlay={() => setPlaying(true)} onPause={() => setPlaying(false)}
        onEnded={() => setPlaying(false)} onError={() => { setPlaying(false); setAudioFailed(true) }} />
      {audioFailed && <p className="cld-error" role="alert">{t.audioError}</p>}
    </section>

    <LandingPricing locale={locale} onCreate={onCreate} />

    <section className="cld-section cld-faq" aria-labelledby="cld-faq-title">
      <header className="cld-section-heading"><p>{t.help}</p><h2 id="cld-faq-title">{t.faq}</h2></header>
      <div>{[...t.questions, ...faqExtra[locale]].map(([question, answer]) => <details key={question}><summary>{question}</summary><p>{answer}</p></details>)}</div>
    </section>

    <section className="cld-final">
      <div><h2>{t.headline}</h2><p>{t.note}</p></div>
      <button type="button" onClick={onCreate}>{t.create}<ArrowRightOutlined aria-hidden="true" /></button>
    </section>
  </div>
}
