'use client'

import { useEffect, useId, useRef, useState } from 'react'
import type { CreatorLocale } from '~/config/creatorLanguages'
import type { PublicProjectDetailRow, PublicProjectVideoRow } from '~/types/business-api'
import { publicProjectDetail, publicProjectVideoList } from '~/utils/business/project'
import { resolveActivePublicEpisode, resolvePublicProjectPlayback } from '~/utils/publicCasePlaza'
import copy from './landingGalleryCopy.json'
import styles from './LandingGallery.module.css'

const PAGE_SIZE = 6
type LocaleProps = { locale: CreatorLocale }
type Result<T> = { data: T } | { failed: true } | null

function WorkPreview({ work, locale, onClose }: LocaleProps & {
  work: PublicProjectVideoRow
  onClose: () => void
}) {
  const dialog = useRef<HTMLDialogElement>(null)
  const titleId = useId()
  const text = copy[locale]
  const [attempt, setAttempt] = useState(0)
  const [result, setResult] = useState<Result<PublicProjectDetailRow>>(null)
  const [episodeId, setEpisodeId] = useState<number | null>(null)
  const [failedVideo, setFailedVideo] = useState('')

  useEffect(() => {
    const element = dialog.current
    element?.showModal()
    return () => element?.close()
  }, [])

  useEffect(() => {
    let active = true
    publicProjectDetail(work.id).then(
      data => { if (active) setResult({ data }) },
      () => { if (active) setResult({ failed: true }) },
    )
    return () => { active = false }
  }, [work.id, attempt])

  const detail = result && 'data' in result ? result.data : null
  const playback = detail ? resolvePublicProjectPlayback(detail, episodeId) : null
  const episodes = detail?.episodes || []
  const activeEpisode = detail ? resolveActivePublicEpisode(detail, episodeId) : null
  const number = new Intl.NumberFormat(locale)

  return (
    <dialog ref={dialog} className={styles.dialog} aria-labelledby={titleId} onCancel={onClose}>
      <header className={styles.dialogHeader}>
        <h3 id={titleId}>{work.projectName || text.project}</h3>
        <button className={styles.control} type="button" onClick={onClose} autoFocus>{text.close}</button>
      </header>
      {!result && <p role="status">{text.loading}</p>}
      {result && 'failed' in result && (
        <div className={styles.feedback} role="alert">
          {text.failure}
          <button type="button" className={styles.control} onClick={() => {
            setResult(null)
            setAttempt(value => value + 1)
          }}>{text.retry}</button>
        </div>
      )}
      {detail && playback && (
        <>
          {playback.videoUrl ? (
            <>
              <video key={playback.videoUrl} className={styles.player} src={playback.videoUrl}
                poster={playback.coverUrl || undefined} controls playsInline preload="metadata"
                onError={() => setFailedVideo(playback.videoUrl)} />
              {failedVideo === playback.videoUrl && <p role="alert">{text.playbackError}</p>}
            </>
          ) : (
            <>
              {playback.coverUrl && <img className={styles.player} src={playback.coverUrl} alt="" />}
              <p>{text.noVideo}</p>
            </>
          )}
          {episodes.length > 1 && (
            <label className={styles.episodes}>
              {text.episode}
              <select value={activeEpisode?.episodeId} onChange={event => {
                setFailedVideo('')
                setEpisodeId(Number(event.target.value))
              }}>
                {episodes.map((episode, index) => (
                  <option key={episode.episodeId} value={episode.episodeId}>
                    {episode.title || number.format(episode.episodeNo || index + 1)}
                  </option>
                ))}
              </select>
            </label>
          )}
          {detail.authorNickname && <p>{detail.authorNickname}</p>}
          {detail.projectDesc && <p>{detail.projectDesc}</p>}
        </>
      )}
    </dialog>
  )
}

export function LandingGallery({ locale }: LocaleProps) {
  const [page, setPage] = useState(1)
  const [attempt, setAttempt] = useState(0)
  const [result, setResult] = useState<Result<{ total: number; rows: PublicProjectVideoRow[] }>>(null)
  const [work, setWork] = useState<PublicProjectVideoRow | null>(null)
  const text = copy[locale]
  const number = new Intl.NumberFormat(locale)

  useEffect(() => {
    let active = true
    publicProjectVideoList({ pageNum: page, pageSize: PAGE_SIZE }).then(
      data => {
        if (!active) return
        const lastPage = Math.max(1, Math.ceil(data.total / PAGE_SIZE))
        if (page > lastPage) setPage(lastPage)
        else setResult({ data })
      },
      () => { if (active) setResult({ failed: true }) },
    )
    return () => { active = false }
  }, [page, attempt])

  function changePage(next: number) {
    setResult(null)
    setPage(next)
  }

  if (!result) return <p className={styles.feedback} role="status">{text.loading}</p>
  if ('failed' in result) return (
    <div className={styles.feedback} role="alert">
      {text.failure}
      <button type="button" className={styles.control} onClick={() => {
        setResult(null)
        setAttempt(value => value + 1)
      }}>{text.retry}</button>
    </div>
  )
  const { total, rows } = result.data
  const pageCount = Math.ceil(total / PAGE_SIZE)
  if (!rows.length) return <p className={styles.feedback} role="status">{text.empty}</p>

  return (
    <>
      <div className={styles.grid}>
        {rows.map(item => (
          <button type="button" className={styles.card} key={item.id} onClick={() => setWork(item)}>
            <span className={styles.cover}>
              {item.coverUrl && <img src={item.coverUrl} alt="" loading="lazy" />}
            </span>
            <strong>{item.projectName || text.project}</strong>
            {item.authorNickname && <small>{item.authorNickname}</small>}
          </button>
        ))}
      </div>
      {pageCount > 1 && (
        <div className={styles.pagination}>
          <button type="button" className={styles.control} disabled={page === 1} onClick={() => changePage(page - 1)}>{text.previous}</button>
          <span aria-live="polite">{number.format(page)} / {number.format(pageCount)}</span>
          <button type="button" className={styles.control} disabled={page >= pageCount} onClick={() => changePage(page + 1)}>{text.next}</button>
        </div>
      )}
      {work && <WorkPreview work={work} locale={locale} onClose={() => setWork(null)} />}
    </>
  )
}
