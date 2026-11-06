'use client'

import { useEffect, useRef, useState } from 'react'
import Link from 'next/link'
import dashboardNav from '~/config/dashboardNav.json'
import { homeCopy } from '~/config/home-locales'
import { useCreatorLocale } from '~/hooks/useCreatorLocale'
import { useOpenLibraryProject } from '~/hooks/useOpenLibraryProject'
import { useUserStore } from '~/stores/user'
import type { UserProjectRow } from '~/types/business-api'
import { userProjectList } from '~/utils/business/project'
import { withAppBasePath } from '~/utils/appBasePath'
import { landingSamples } from '~/utils/landingSamples'
import copy from './landingGalleryCopy.json'
import './DashboardProjects.css'

export default function DashboardProjects({ onClose }: { onClose: () => void }) {
  const { locale } = useCreatorLocale()
  const t = homeCopy[locale]
  const text = copy[locale]
  const token = useUserStore(state => state.token)
  const openProject = useOpenLibraryProject()
  const dialog = useRef<HTMLDialogElement>(null)
  const [page, setPage] = useState(1)
  const [attempt, setAttempt] = useState(0)
  const [result, setResult] = useState<{ owner: string; page: number; total: number; rows: UserProjectRow[] } | null>(null)
  const [error, setError] = useState(false)
  const current = result?.owner === token && result.page === page ? result : null

  useEffect(() => {
    const element = dialog.current
    element?.showModal()
    return () => element?.close()
  }, [])

  useEffect(() => {
    if (!token) return
    let active = true
    setError(false)
    userProjectList({ pageNum: page, pageSize: 24 }).then(
      data => { if (active) setResult({ ...data, page, owner: token }) },
      () => { if (active) setError(true) }
    )
    return () => { active = false }
  }, [token, page, attempt])

  return (
    <dialog ref={dialog} className="dashboard-projects" aria-labelledby="dashboard-projects-title"
      onCancel={event => { event.preventDefault(); onClose() }}
      onClick={event => { if (event.target === event.currentTarget) onClose() }}>
      <div className="dashboard-projects__sheet">
        <header>
          <h2 id="dashboard-projects-title">{dashboardNav[locale][1]}</h2>
          {(!token || current) && <span>{landingSamples.length + (current?.total || 0)}</span>}
          <button type="button" autoFocus onClick={onClose} aria-label={text.close}>×</button>
        </header>
        <div className="dashboard-projects__body">
          {token && !current && !error && <p role="status">{text.loading}</p>}
          {token && error && <p role="alert">{text.failure} <button type="button" onClick={() => setAttempt(x => x + 1)}>{text.retry}</button></p>}
          <div className="dashboard-projects__grid">
            {current?.rows.map(project => (
              <button type="button" key={project.id} className="dashboard-projects__card"
                onClick={() => void openProject(project.id, project.projectType)}>
                <span className="dashboard-projects__cover">
                  {project.coverUrl && <img src={project.coverUrl} alt="" loading="lazy" />}
                </span>
                <strong>{project.projectName}</strong>
                <small>{[project.aspectRatio, (project.updateTime || project.createTime)?.slice(0, 10)].filter(Boolean).join(' · ')}</small>
              </button>
            ))}
            {page === 1 && landingSamples.map((sample, i) => (
              <Link key={sample.id} className="dashboard-projects__card" href={`/case?sample=${sample.id}`}>
                <span className="dashboard-projects__cover">
                  <img src={withAppBasePath(sample.poster)} alt="" loading="lazy" />
                  <b>{t.samples}</b>
                </span>
                <strong>{t.films[i][0]}</strong>
                <small>{t.films[i][1]}</small>
              </Link>
            ))}
          </div>
          {current && current.total > 24 && (
            <div className="dashboard-projects__paging">
              <button type="button" disabled={page === 1} onClick={() => setPage(page - 1)}>{text.previous}</button>
              <span>{page} / {Math.ceil(current.total / 24)}</span>
              <button type="button" disabled={page * 24 >= current.total} onClick={() => setPage(page + 1)}>{text.next}</button>
            </div>
          )}
        </div>
      </div>
    </dialog>
  )
}
