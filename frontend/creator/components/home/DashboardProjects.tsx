'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import { homeCopy } from '~/config/home-locales'
import { withAppBasePath } from '~/utils/appBasePath'
import { landingSamples } from '~/utils/landingSamples'
import dashboardNav from '~/config/dashboardNav.json'
import { useCreatorLocale } from '~/hooks/useCreatorLocale'
import { useOpenLibraryProject } from '~/hooks/useOpenLibraryProject'
import { useUserStore } from '~/stores/user'
import type { UserProjectRow } from '~/types/business-api'
import { userProjectList } from '~/utils/business/project'
import { requireLogin } from '~/utils/authLoginNavigation'
import copy from './landingGalleryCopy.json'
import './DashboardProjects.css'

export default function DashboardProjects() {
  const { locale } = useCreatorLocale()
  const text = copy[locale]
  const t = homeCopy[locale]
  const chinese = locale === 'zh' || locale === 'zh-hant'
  const token = useUserStore(state => state.token)
  const openProject = useOpenLibraryProject()
  const [page, setPage] = useState(1)
  const [attempt, setAttempt] = useState(0)
  const [result, setResult] = useState<{ owner: string; page: number; total: number; rows: UserProjectRow[] } | null>(null)
  const [error, setError] = useState(false)
  const current = result?.owner === token && result.page === page ? result : null

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
    <main className="dashboard-projects" aria-labelledby="dashboard-projects-title">
      <header className="dashboard-projects__header">
        <div>
          <p className="dashboard-projects__eyebrow">{chinese ? '创作空间' : 'Your workspace'}</p>
          <h1 id="dashboard-projects-title">{dashboardNav[locale][1]}</h1>
          <p>{chinese ? '继续你的故事，或从示例中寻找新的灵感。' : 'Continue your story, or find inspiration in the samples.'}</p>
        </div>
        <div className="dashboard-projects__actions">
          <Link className="dashboard-projects__manage" href="/works/">{chinese ? '管理作品' : 'Manage works'}</Link>
          <Link className="dashboard-projects__create" href="/start/">{chinese ? '开始创作' : 'Start creating'} <span aria-hidden="true">↗</span></Link>
        </div>
      </header>

      <section className="dashboard-projects__section" aria-labelledby="personal-projects-title">
        <div className="dashboard-projects__section-heading">
          <h2 id="personal-projects-title">{chinese ? '我的项目' : 'My projects'}</h2>
          {current && <span>{current.total}</span>}
        </div>
        {token && !current && !error && <p className="dashboard-projects__notice" role="status">{text.loading}</p>}
        {token && error && <p className="dashboard-projects__notice" role="alert">{text.failure} <button type="button" onClick={() => setAttempt(x => x + 1)}>{text.retry}</button></p>}
        {!token && <p className="dashboard-projects__notice">{chinese ? '登录后即可查看和继续你的项目，也可以先浏览下方示例。' : 'Sign in to view and resume your projects, or explore the samples below.'} <button type="button" onClick={() => requireLogin({ redirect: '/projects/', leaveAuthRoute: false })}>{chinese ? '登录查看' : 'Sign in'}</button></p>}
        {current?.total === 0 && <p className="dashboard-projects__notice">{chinese ? '暂无个人项目。从创作台开始你的第一个故事，也可以先浏览下方示例。' : 'No personal projects yet. Start your first story in the studio, or explore the samples below.'}</p>}
        <div className="dashboard-projects__grid">
          {current?.rows.map(project => (
            <button type="button" key={project.id} className="dashboard-projects__card"
              onClick={() => void openProject(project.id, project.projectType)}>
              <span className="dashboard-projects__cover">
                {project.coverUrl ? <img src={project.coverUrl} alt="" loading="lazy" /> : <span className="dashboard-projects__placeholder" aria-hidden="true">{project.projectName?.slice(0, 1) || '✦'}</span>}
                <b>{chinese ? '继续创作' : 'Continue'}</b>
              </span>
              <strong>{project.projectName}</strong>
              <small>{[project.aspectRatio, (project.updateTime || project.createTime)?.slice(0, 10)].filter(Boolean).join(' · ')}</small>
            </button>
          ))}
        </div>
        {current && current.total > 24 && (
          <div className="dashboard-projects__paging">
            <button type="button" disabled={page === 1} onClick={() => setPage(page - 1)}>{text.previous}</button>
            <span>{page} / {Math.ceil(current.total / 24)}</span>
            <button type="button" disabled={page * 24 >= current.total} onClick={() => setPage(page + 1)}>{text.next}</button>
          </div>
        )}
      </section>

      <section className="dashboard-projects__section" aria-labelledby="sample-projects-title">
        <div className="dashboard-projects__section-heading">
          <h2 id="sample-projects-title">{chinese ? '灵感示例' : 'Inspiration'}</h2>
          <span>{landingSamples.length}</span>
        </div>
        <p className="dashboard-projects__section-description">{chinese ? '看看不同的故事与风格，为下一次创作找到方向。' : 'Explore different stories and styles for your next creation.'}</p>
        <div className="dashboard-projects__grid">
          {landingSamples.map((sample, index) => (
            <Link key={sample.id} className="dashboard-projects__card" href={`/case?sample=${sample.id}`}>
              <span className="dashboard-projects__cover">
                <img src={withAppBasePath(sample.poster)} alt="" loading="lazy" />
                <b>{t.samples}</b>
              </span>
              <strong>{t.films[index][0]}</strong>
              <small>{t.films[index][1]}</small>
            </Link>
          ))}
        </div>
      </section>
    </main>
  )
}
