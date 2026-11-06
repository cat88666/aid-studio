'use client'

import Link from 'next/link'
import { useEffect, useRef, useState } from 'react'
import { PaperClipOutlined, CaretRightFilled } from '@ant-design/icons'
import { useCreatorLocale } from '~/hooks/useCreatorLocale'
import { useHomeShellCreateModal } from '~/hooks/useHomeShellCreateModal'
import { homeCopy } from '~/config/home-locales'
import type { CreatorLocale } from '~/config/creatorLanguages'
import { useLoginModalStore } from '~/stores/loginModal'
import { useUserStore } from '~/stores/user'
import { requireLogin } from '~/utils/authLoginNavigation'
import { withAppBasePath } from '~/utils/appBasePath'
import { landingSamples } from '~/utils/landingSamples'
import { assertScriptPlainTextFile, SCRIPT_UPLOAD_ACCEPT, validateScriptUploadFile } from '~/utils/scriptFileUpload'
import './CreatorDashboard.css'

const MAX_CHARACTERS = 100_000
const copy: Record<CreatorLocale, [string, string, string, string, string, string]> = {
  zh: ['导入剧本','空白项目','粘贴你的剧本，或上传剧本文件…','项目名称','上传文件','请使用不超过 10 万字的 TXT 纯文本文件。'],
  'zh-hant': ['匯入劇本','空白專案','貼上你的劇本，或上傳劇本檔案…','專案名稱','上傳檔案','請使用不超過 10 萬字的 TXT 純文字檔。'],
  ja: ['脚本を読み込む','新規プロジェクト','脚本を貼り付けるか、ファイルをアップロード…','プロジェクト名','ファイルを選択','10万文字以内の TXT ファイルを選択してください。'],
  en: ['Import script','Blank project','Paste your script or upload a text file…','Project title','Upload file','Choose a plain TXT file of up to 100,000 characters.'],
  ko: ['대본 가져오기','빈 프로젝트','대본을 붙여 넣거나 파일을 업로드하세요…','프로젝트 이름','파일 업로드','10만 자 이내의 TXT 파일을 선택하세요.'],
  fr: ['Importer un scénario','Projet vierge','Collez votre scénario ou importez un fichier…','Titre du projet','Importer un fichier','Choisissez un fichier TXT de 100 000 caractères maximum.'],
  de: ['Drehbuch importieren','Leeres Projekt','Drehbuch einfügen oder Textdatei hochladen…','Projekttitel','Datei hochladen','TXT-Datei mit höchstens 100.000 Zeichen wählen.'],
  it: ['Importa copione','Progetto vuoto','Incolla il copione o carica un file di testo…','Titolo del progetto','Carica file','Scegli un file TXT fino a 100.000 caratteri.'],
  es: ['Importar guion','Proyecto vacío','Pega tu guion o sube un archivo de texto…','Título del proyecto','Subir archivo','Elige un archivo TXT de hasta 100.000 caracteres.'],
  pt: ['Importar roteiro','Projeto em branco','Cole seu roteiro ou envie um arquivo…','Título do projeto','Enviar arquivo','Escolha um TXT com até 100.000 caracteres.'],
  id: ['Impor naskah','Proyek kosong','Tempel naskah atau unggah berkas teks…','Judul proyek','Unggah berkas','Pilih berkas TXT hingga 100.000 karakter.'],
  ru: ['Импорт сценария','Пустой проект','Вставьте сценарий или загрузите текстовый файл…','Название проекта','Загрузить файл','Выберите TXT-файл длиной до 100 000 символов.'],
  tr: ['Senaryo aktar','Boş proje','Senaryoyu yapıştırın veya metin dosyası yükleyin…','Proje adı','Dosya yükle','En fazla 100.000 karakterlik bir TXT dosyası seçin.'],
  nl: ['Script importeren','Leeg project','Plak je script of upload een tekstbestand…','Projecttitel','Bestand uploaden','Kies een TXT-bestand met maximaal 100.000 tekens.'],
  uk: ['Імпорт сценарію','Порожній проєкт','Вставте сценарій або завантажте текстовий файл…','Назва проєкту','Завантажити файл','Виберіть TXT-файл довжиною до 100 000 символів.'],
  ar: ['استيراد نص','مشروع فارغ','الصق النص أو ارفع ملفًا نصيًا…','اسم المشروع','رفع ملف','اختر ملف TXT لا يتجاوز ١٠٠٬٠٠٠ حرف.']
}

export default function CreatorDashboard() {
  const { locale } = useCreatorLocale()
  const t = homeCopy[locale]
  const [importMode, blankMode, prompt, projectTitle, upload, fileHint] = copy[locale]
  const [mode, setMode] = useState<'script' | 'blank'>('script')
  const [script, setScript] = useState('')
  const [name, setName] = useState('')
  const [filename, setFilename] = useState('')
  const [reading, setReading] = useState(false)
  const [fileError, setFileError] = useState(false)
  const [preview, setPreview] = useState('')
  const fileInput = useRef<HTMLInputElement>(null)
  const create = useHomeShellCreateModal()
  const token = useUserStore(state => state.token)
  const loginOpen = useLoginModalStore(state => state.open)
  const resumeCreate = useRef<(() => void) | null>(null)
  useEffect(() => {
    if (loginOpen || !resumeCreate.current) return
    const resume = resumeCreate.current
    resumeCreate.current = null
    if (token) resume()
  }, [loginOpen, token])

  async function importFile(file?: File) {
    if (!file) return
    setReading(true)
    setFileError(false)
    try {
      if (validateScriptUploadFile(file) || file.size > 15 * 1024 * 1024) throw new Error()
      await assertScriptPlainTextFile(file)
      const text = await file.text()
      if (!text.trim() || text.length > MAX_CHARACTERS) throw new Error()
      setScript(text)
      setFilename(file.name)
    } catch {
      setFileError(true)
    } finally {
      setReading(false)
    }
  }

  function openProject() {
    if (mode === 'blank') {
      create.openCreateModal()
      if (name.trim()) create.setCreationTitleDraft(name.trim())
      return
    }
    if (!script.trim()) return
    const file = new File([script], filename || 'script.txt', { type: 'text/plain' })
    create.openCreateModal({ worksTab: 'series', scriptFile: file })
    if (filename) create.setCreationTitleDraft(filename.replace(/\.txt$/i, '').slice(0, 50))
  }

  function start() {
    if (token) return openProject()
    resumeCreate.current = openProject
    requireLogin({ redirect: null, leaveAuthRoute: false })
  }

  return <main className="creator-desk" lang={locale} dir={locale === 'ar' ? 'rtl' : 'ltr'}>
    <header className="creator-desk-hero"><h1>{t.headline}</h1><p>{t.intro}</p></header>
    <section className="creator-desk-compose" aria-label={t.create}>
      <div className="creator-desk-modes" role="group" aria-label={t.create}>
        <button type="button" aria-pressed={mode === 'script'} onClick={() => setMode('script')}>{importMode}</button>
        <button type="button" aria-pressed={mode === 'blank'} onClick={() => setMode('blank')}>{blankMode}</button>
      </div>
      <form className="creator-desk-input" onSubmit={event => { event.preventDefault(); start() }}>
        {mode === 'script'
          ? <textarea aria-label={importMode} placeholder={prompt} value={script} maxLength={MAX_CHARACTERS} disabled={reading}
              onChange={event => { setScript(event.target.value); setFileError(false) }} rows={4} />
          : <input type="text" aria-label={projectTitle} placeholder={projectTitle} maxLength={50} value={name} onChange={event => setName(event.target.value)} />}
        <div className="creator-desk-toolbar">
          {mode === 'script' && <>
            <input type="file" ref={fileInput} accept={SCRIPT_UPLOAD_ACCEPT} hidden onChange={event => {
              const file = event.target.files?.[0]
              event.target.value = ''
              void importFile(file)
            }} />
            <button className="creator-desk-upload" type="button" disabled={reading} onClick={() => fileInput.current?.click()}>
              <PaperClipOutlined />{upload}
            </button>
            {filename && <span className="creator-desk-file" title={filename}>{filename}</span>}
          </>}
          <button type="submit" className="creator-desk-start" disabled={reading || (mode === 'script' && !script.trim())}>{t.create}</button>
        </div>
      </form>
      {mode === 'script' && <p className={fileError ? 'creator-desk-note has-error' : 'creator-desk-note'} role={fileError ? 'alert' : undefined}>{fileHint}</p>}
    </section>
    <section className="creator-desk-examples">
      <div className="creator-desk-examples-head"><h2>{t.examples}</h2><Link href="/gallery/">{t.gallery} <span aria-hidden="true">→</span></Link></div>
      <div className="creator-desk-grid">
        {landingSamples.map((sample, index) => <Link key={sample.id} href={'/case?sample=' + sample.id} className="creator-desk-card"
          onPointerEnter={event => {
            if (event.pointerType === 'mouse' && !window.matchMedia('(prefers-reduced-motion: reduce)').matches) setPreview(sample.id)
          }}
          onPointerLeave={() => setPreview('')}>
          <div className="creator-desk-cover">
            <img src={withAppBasePath(sample.poster)} alt="" loading={index < 5 ? 'eager' : 'lazy'} />
            {preview === sample.id && <video autoPlay muted loop playsInline aria-hidden="true" poster={withAppBasePath(sample.poster)}
              src={withAppBasePath(sample.video)} onError={() => setPreview('')} />}
            <span className="creator-desk-preview"><CaretRightFilled />{t.watch}</span>
          </div>
          <div className="creator-desk-caption"><h3>{t.films[index][0]}</h3><p>{t.films[index][1]}</p></div>
        </Link>)}
      </div>
    </section>
    <footer className="creator-desk-footer"><Link href="/guide/">{t.guide}</Link><Link href="/faq/">{t.helpCenter}</Link></footer>
  </main>
}
