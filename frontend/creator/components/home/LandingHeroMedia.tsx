'use client'

import { useEffect, useRef, useState } from 'react'
import { CaretRightOutlined, PauseOutlined } from '@ant-design/icons'
import { withAppBasePath } from '~/utils/appBasePath'

export function LandingHeroMedia({ playLabel, pauseLabel }: { playLabel: string; pauseLabel: string }) {
  const player = useRef<HTMLVideoElement>(null)
  const [playing, setPlaying] = useState(false)
  const [failed, setFailed] = useState(false)
  const poster = withAppBasePath('/creator-home/hero.webp')

  function play() {
    const video = player.current
    if (!video) return
    if (!video.hasAttribute('src')) video.src = withAppBasePath('/media/login/login-video-bg.mp4')
    void video.play().catch(() => setPlaying(false))
  }

  useEffect(() => {
    const motion = window.matchMedia('(prefers-reduced-motion: reduce)')
    const compact = window.matchMedia('(max-width: 720px)')
    const connection = (navigator as Navigator & { connection?: { saveData?: boolean } }).connection
    if (!motion.matches && !compact.matches && !connection?.saveData) play()
    const onChange = () => { if (motion.matches) player.current?.pause() }
    motion.addEventListener('change', onChange)
    return () => motion.removeEventListener('change', onChange)
  }, [])

  return <>
    <video className="creator-hero-image" poster={poster} muted playsInline loop ref={player}
      onPlay={() => setPlaying(true)} onPause={() => setPlaying(false)}
      onError={() => setFailed(true)} aria-hidden="true" />
    {!failed && <button type="button" className="creator-hero-motion"
      aria-label={playing ? pauseLabel : playLabel}
      onClick={() => playing ? player.current?.pause() : play()}>
      {playing ? <PauseOutlined /> : <CaretRightOutlined />}
    </button>}
  </>
}
