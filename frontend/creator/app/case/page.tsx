'use client'

import { useRouter, useSearchParams } from 'next/navigation'
import { Suspense, useEffect, useState } from 'react'
import { HtmlShellClass } from '~/components/app/HtmlShellClass'
import type { PublicProjectDetailRow } from '~/types/business-api'
import Link from 'next/link'
import { creatorBrandName } from '~/config/creatorBrand'
import { creatorLanguage } from '~/config/creatorLanguages'
import { homeCopy } from '~/config/home-locales'
import { useCreatorLocale } from '~/hooks/useCreatorLocale'
import { withAppBasePath } from '~/utils/appBasePath'
import { landingSamples } from '~/utils/landingSamples'
import { publicProjectDetail } from '~/utils/businessApi'
import {
  resolveActivePublicEpisode,
  resolvePublicProjectPlayback,
  resolvePublicProjectType
} from '~/utils/publicCasePlaza'
import './case-page.css'

function parseProjectId(value: string | null): number {
  const id = Number(value)
  return Number.isSafeInteger(id) && id > 0 ? id : 0
}

function CaseDetailContent() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const { locale } = useCreatorLocale()
  const t = homeCopy[locale]
  const sampleId = searchParams.get('sample')
  const sampleIndex = landingSamples.findIndex(item => item.id === sampleId)
  const sample = landingSamples[sampleIndex]
  const projectId = sampleId === null ? parseProjectId(searchParams.get('id')) : 0
  const [requestState, setRequestState] = useState<{
    projectId: number
    detail: PublicProjectDetailRow | null
    errorMessage: string
  }>({ projectId: 0, detail: null, errorMessage: '' })
  const [activeEpisodeId, setActiveEpisodeId] = useState<number | null>(null)

  useEffect(() => {
    let cancelled = false
    if (!projectId) {
      return () => {
        cancelled = true
      }
    }

    void publicProjectDetail(projectId)
      .then((row) => {
        if (cancelled) return
        setRequestState({ projectId, detail: row, errorMessage: '' })
        const firstEpisode = Array.isArray(row.episodes) ? row.episodes[0] : null
        setActiveEpisodeId(firstEpisode?.episodeId ?? null)
      })
      .catch(() => {
        if (cancelled) return
        setRequestState({
          projectId,
          detail: null,
          errorMessage: '案例详情加载失败，请稍后重试'
        })
      })

    return () => {
      cancelled = true
    }
  }, [projectId])

  const loading = projectId > 0 && requestState.projectId !== projectId
  const detail = requestState.projectId === projectId ? requestState.detail : null
  const errorMessage = !projectId
    ? '案例地址无效'
    : requestState.projectId === projectId
      ? requestState.errorMessage
      : ''
  const episodes = Array.isArray(detail?.episodes) ? detail.episodes : []
  const activeEpisode = detail ? resolveActivePublicEpisode(detail, activeEpisodeId) : null
  const playback = detail
    ? resolvePublicProjectPlayback(detail, activeEpisodeId)
    : { videoUrl: '', coverUrl: '' }
  const projectType = detail ? resolvePublicProjectType(detail.projectType) : 'movie'

  return (
    <div className="public-case-page" lang={locale} dir={locale === 'ar' ? 'rtl' : 'ltr'}>
      <HtmlShellClass classes="public-case-shell" />
      <nav className="public-case-nav" aria-label={t.nav}>
        <Link href={creatorLanguage(locale).path}>{creatorBrandName(locale)}</Link>
        <span aria-hidden="true">/</span>
        <Link href="/gallery">{t.examples}</Link>
      </nav>

      {sample ? (
        <main className="public-case-content">
          <header className="public-case-info">
            <h1>{t.films[sampleIndex][0]}</h1>
            <p>{t.films[sampleIndex][1]}</p>
          </header>
          <div className="public-case-player">
            <video key={sample.id} controls playsInline preload="metadata" aria-label={t.player}
              poster={withAppBasePath(sample.poster)} src={withAppBasePath(sample.video)} />
          </div>
        </main>
      ) : loading ? (
        <div className="public-case-feedback" role="status">案例加载中...</div>
      ) : errorMessage || !detail ? (
        <div className="public-case-feedback" role="alert">
          <p>{errorMessage || '案例不存在或尚未公开'}</p>
          <button type="button" onClick={() => router.push('/gallery')}>返回案例广场</button>
        </div>
      ) : (
        <main className="public-case-content">
          <header className="public-case-info">
            <h1>{detail.projectName || `公开项目 #${detail.id}`}</h1>
            <p className="public-case-type">
              {projectType === 'series' ? `电视剧集${detail.episodeCount ? ` · ${detail.episodeCount}集` : ''}` : '电影/短片'}
            </p>
            {detail.publishTime ? <p className="public-case-published">发布于 {detail.publishTime}</p> : null}
            {detail.authorNickname && <p className="public-case-author">{detail.authorNickname}</p>}
          </header>
          <section className="public-case-media" aria-label="案例视频">
            <div className="public-case-player">
              {playback.videoUrl ? (
                <video
                  key={playback.videoUrl}
                  src={playback.videoUrl}
                  poster={playback.coverUrl || undefined}
                  controls
                  autoPlay
                  playsInline
                  preload="metadata"
                />
              ) : playback.coverUrl ? (
                <img src={playback.coverUrl} alt={detail.projectName} />
              ) : (
                <div className="public-case-no-media">该案例暂无可播放视频</div>
              )}
            </div>

            {episodes.length > 1 ? (
              <div className="public-case-episodes" aria-label="剧集列表">
                {episodes.map((episode, index) => (
                  <button
                    key={episode.episodeId}
                    type="button"
                    className={activeEpisode?.episodeId === episode.episodeId ? 'is-active' : ''}
                    aria-pressed={activeEpisode?.episodeId === episode.episodeId}
                    onClick={() => setActiveEpisodeId(episode.episodeId)}
                  >
                    {episode.coverUrl ? <img src={episode.coverUrl} alt="" loading="lazy" /> : null}
                    <span>{episode.title || `第${episode.episodeNo || index + 1}集`}</span>
                  </button>
                ))}
              </div>
            ) : null}
          </section>
          {detail.projectDesc?.trim() && <p className="public-case-description">{detail.projectDesc.trim()}</p>}

        </main>
      )}
    </div>
  )
}

/** 静态 `/case?id=` 详情页，兼容开源版静态导出。 */
export default function CaseDetailPage() {
  return (
    <Suspense fallback={<div className="public-case-feedback">案例加载中...</div>}>
      <CaseDetailContent />
    </Suspense>
  )
}
