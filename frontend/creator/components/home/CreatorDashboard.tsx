'use client'

import { useEffect, useRef, useState } from 'react'
import { PaperClipOutlined, ArrowRightOutlined, VideoCameraOutlined } from '@ant-design/icons'
import { useCreatorLocale } from '~/hooks/useCreatorLocale'
import { useHomeShellCreateModal } from '~/hooks/useHomeShellCreateModal'
import { useOpenLibraryProject } from '~/hooks/useOpenLibraryProject'
import { useLoginModalStore } from '~/stores/loginModal'
import { useUserStore } from '~/stores/user'
import type { UserProjectRow } from '~/types/business-api'
import { userProjectList } from '~/utils/business/project'
import { requireLogin } from '~/utils/authLoginNavigation'
import { assertScriptPlainTextFile, SCRIPT_UPLOAD_ACCEPT, validateScriptUploadFile } from '~/utils/scriptFileUpload'
import './CreatorDashboard.css'

const MAX_CHARACTERS = 100_000
const copy = {
  en: {
    title: 'Studio', create: 'New project', movie: 'Film / short', series: 'Series', import: 'Import script',
    name: 'Project name', untitled: 'Untitled project', prompt: 'Paste your script…', upload: 'Upload TXT',
    hint: 'TXT · up to 100,000 characters', invalid: 'Choose a non-empty TXT file under 15 MB and 100,000 characters.',
    next: 'Continue', recent: 'Recent projects', login: 'Sign in', loginHint: 'Sign in to continue your projects.',
    empty: 'No projects yet.', loading: 'Loading projects…', failure: 'Could not load projects.', retry: 'Retry',
    resume: 'Continue project', opening: 'Opening…', episodes: 'Episodes', draft: 'Draft',
    steps: ['Project setup', 'Script', 'Characters & scenes', 'Storyboard', 'Video', 'Voiceover', 'Preview'],
  },
  zh: {
    title: '创作台', create: '新建作品', movie: '电影 / 短片', series: '连续剧', import: '导入剧本',
    name: '作品名称', untitled: '未命名作品', prompt: '粘贴剧本…', upload: '上传 TXT',
    hint: 'TXT · 最多 10 万字', invalid: '请选择非空 TXT 文件，不超过 15 MB 和 10 万字。',
    next: '继续', recent: '最近项目', login: '登录', loginHint: '登录后继续你的项目。',
    empty: '暂无项目', loading: '正在加载项目…', failure: '项目加载失败', retry: '重试',
    resume: '继续创作', opening: '正在打开…', episodes: '分集', draft: '草稿',
    steps: ['项目配置', '剧本', '角色与场景', '分镜', '视频', '配音', '预览'],
  },
  'zh-hant': {
    title: '創作台', create: '新建作品', movie: '電影 / 短片', series: '連續劇', import: '匯入劇本',
    name: '作品名稱', untitled: '未命名作品', prompt: '貼上劇本…', upload: '上傳 TXT',
    hint: 'TXT · 最多 10 萬字', invalid: '請選擇非空 TXT 檔案，不超過 15 MB 和 10 萬字。',
    next: '繼續', recent: '最近專案', login: '登入', loginHint: '登入後繼續你的專案。',
    empty: '暫無專案', loading: '正在載入專案…', failure: '專案載入失敗', retry: '重試',
    resume: '繼續創作', opening: '正在開啟…', episodes: '分集', draft: '草稿',
    steps: ['專案設定', '劇本', '角色與場景', '分鏡', '影片', '配音', '預覽'],
  },
}

type ProjectResult = { owner: string; attempt: number; rows: UserProjectRow[]; error: boolean }

export default function CreatorDashboard() {
  const { locale } = useCreatorLocale()
  const text = copy[locale === 'zh' || locale === 'zh-hant' ? locale : 'en']
  const [mode, setMode] = useState<'movie' | 'series' | 'import'>('movie')
  const [script, setScript] = useState('')
  const [name, setName] = useState('')
  const [filename, setFilename] = useState('')
  const [reading, setReading] = useState(false)
  const [fileError, setFileError] = useState(false)
  const [attempt, setAttempt] = useState(0)
  const [result, setResult] = useState<ProjectResult | null>(null)
  const [opening, setOpening] = useState<number | null>(null)
  const fileInput = useRef<HTMLInputElement>(null)
  const create = useHomeShellCreateModal()
  const openProject = useOpenLibraryProject()
  const token = useUserStore(state => state.token)
  const loginOpen = useLoginModalStore(state => state.open)
  const resumeCreate = useRef<(() => void) | null>(null)
  const current = result?.owner === token && result.attempt === attempt ? result : null

  useEffect(() => {
    if (loginOpen || !resumeCreate.current) return
    const resume = resumeCreate.current
    resumeCreate.current = null
    if (token) resume()
  }, [loginOpen, token])

  useEffect(() => {
    if (!token) return
    let active = true
    void userProjectList({ pageNum: 1, pageSize: 6 }).then(
      data => { if (active) setResult({ owner: token, attempt, rows: data.rows, error: false }) },
      () => { if (active) setResult({ owner: token, attempt, rows: [], error: true }) },
    )
    return () => { active = false }
  }, [token, attempt])

  async function importFile(file?: File) {
    if (!file) return
    setReading(true)
    setFileError(false)
    try {
      if (validateScriptUploadFile(file) || file.size > 15 * 1024 * 1024) throw new Error()
      await assertScriptPlainTextFile(file)
      const contents = await file.text()
      if (!contents.trim() || contents.length > MAX_CHARACTERS) throw new Error()
      setScript(contents)
      setFilename(file.name)
    } catch {
      setFileError(true)
    } finally {
      setReading(false)
    }
  }

  function configureProject() {
    if (mode === 'import') {
      if (!script.trim()) return
      const file = new File([script], filename || 'script.txt', { type: 'text/plain' })
      create.openCreateModal({ worksTab: 'series', scriptFile: file })
    } else {
      create.openCreateModal({ worksTab: mode === 'movie' ? 'film' : 'series' })
    }
    const importedName = mode === 'import' ? filename.replace(/\.txt$/i, '') : ''
    create.setCreationTitleDraft((name.trim() || importedName || text.untitled).slice(0, 25))
  }

  function start() {
    if (token) return configureProject()
    resumeCreate.current = configureProject
    requireLogin({ redirect: null, leaveAuthRoute: false })
  }

  async function resume(project: UserProjectRow) {
    if (opening !== null) return
    setOpening(project.id)
    try { await openProject(project.id, project.projectType) }
    finally { setOpening(null) }
  }

  function stepLabel(project: UserProjectRow) {
    if (project.projectType === 'series') return text.episodes
    const step = Number(project.currentStep)
    return Number.isInteger(step) && step >= 1 && step <= text.steps.length ? text.steps[step - 1] : text.draft
  }

  return <main className="creator-desk" lang={locale === 'zh' || locale === 'zh-hant' ? locale : 'en'} dir={locale === 'ar' ? 'rtl' : 'ltr'}>
    <h1>{text.title}</h1>
    <div className="creator-desk-layout">
      <section className="creator-desk-compose" aria-labelledby="creator-new-project">
        <h2 id="creator-new-project">{text.create}</h2>
        <div className="creator-desk-modes" role="group" aria-label={text.create}>
          {(['movie', 'series', 'import'] as const).map(value => <button type="button" key={value}
            aria-pressed={mode === value} onClick={() => setMode(value)}>{text[value]}</button>)}
        </div>
        <form onSubmit={event => { event.preventDefault(); start() }}>
          <label className="creator-desk-field">{text.name}
            <input type="text" placeholder={text.untitled} maxLength={25} value={name} onChange={event => setName(event.target.value)} />
          </label>
          {mode === 'import' && <div className="creator-desk-import">
            <label className="creator-desk-field">{text.import}
              <textarea placeholder={text.prompt} value={script} maxLength={MAX_CHARACTERS} disabled={reading}
                onChange={event => { setScript(event.target.value); setFileError(false) }} rows={6} />
            </label>
            <input type="file" ref={fileInput} accept={SCRIPT_UPLOAD_ACCEPT} hidden onChange={event => {
              const file = event.target.files?.[0]
              event.target.value = ''
              void importFile(file)
            }} />
            <div className="creator-desk-toolbar">
              <button className="creator-desk-upload" type="button" disabled={reading} onClick={() => fileInput.current?.click()}>
                <PaperClipOutlined aria-hidden="true" />{text.upload}
              </button>
              {filename && <span className="creator-desk-file" title={filename}>{filename}</span>}
            </div>
            <p className={fileError ? 'creator-desk-note has-error' : 'creator-desk-note'} role={fileError ? 'alert' : undefined}>
              {fileError ? text.invalid : text.hint}
            </p>
          </div>}
          <button type="submit" className="creator-desk-start" disabled={reading || (mode === 'import' && !script.trim())}>
            {text.next}<ArrowRightOutlined aria-hidden="true" />
          </button>
        </form>
      </section>
      <section className="creator-desk-recent" aria-labelledby="creator-recent-projects">
        <h2 id="creator-recent-projects">{text.recent}</h2>
        {!token ? <div className="creator-desk-empty"><p>{text.loginHint}</p><button type="button"
          onClick={() => requireLogin({ redirect: null, leaveAuthRoute: false })}>{text.login}</button></div>
          : !current ? <p className="creator-desk-empty" role="status">{text.loading}</p>
          : current.error ? <div className="creator-desk-empty" role="alert"><p>{text.failure}</p>
            <button type="button" onClick={() => setAttempt(value => value + 1)}>{text.retry}</button></div>
          : !current.rows.length ? <p className="creator-desk-empty">{text.empty}</p>
          : <div className="creator-desk-projects">{current.rows.map(project => {
            const updated = project.updateTime || project.createTime
            return <button type="button" className="creator-desk-project" key={project.id} disabled={opening !== null}
              aria-label={`${opening === project.id ? text.opening : text.resume}: ${project.projectName}`}
              onClick={() => void resume(project)}>
              <span className="creator-desk-cover" aria-hidden="true">
                <VideoCameraOutlined />
                {project.coverUrl && <img src={project.coverUrl} alt="" loading="lazy" onError={event => { event.currentTarget.hidden = true }} />}
              </span>
              <span className="creator-desk-project-info"><strong>{project.projectName}</strong>
                <small>{project.projectType === 'series' ? text.series : text.movie} · {stepLabel(project)}</small>
                {updated && <time dateTime={updated}>{updated.slice(0, 16).replace('T', ' ')}</time>}
              </span>
              <ArrowRightOutlined aria-hidden="true" />
            </button>
          })}</div>}
      </section>
    </div>
  </main>
}
