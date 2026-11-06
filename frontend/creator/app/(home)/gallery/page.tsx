'use client'

import Link from 'next/link'
import { homeCopy } from '~/config/home-locales'
import { useCreatorLocale } from '~/hooks/useCreatorLocale'
import publicCopy from '~/components/home/landingGalleryCopy.json'
import { landingSamples } from '~/utils/landingSamples'
import filterLabels from './filterLabels.json'
import './gallery.css'
import { SearchOutlined } from '@ant-design/icons'
import { useRouter } from 'next/navigation'
import { useEffect, useMemo, useRef, useState } from 'react'
import emptyImageIconRaw from '~/assets/img/icon/empty_icon.svg'
import { useHomeShellCreateModal } from '~/composables/useHomeShellCreateModal'
import { useUserStore } from '~/stores/user'
import type { UserProjectType } from '~/types/business-api'
import { withAppBasePath } from '~/utils/appBasePath'
import { assetUrl } from '~/utils/assetUrl'
import { requireLogin } from '~/utils/authLoginNavigation'
import { publicProjectVideoList } from '~/utils/businessApi'
import { normalizePublicCaseCard, type PublicCaseCardItem } from '~/utils/publicCasePlaza'

const emptyImageIconUrl = assetUrl(emptyImageIconRaw)

const filterTabs = [
  { label: '全部', value: 'all' },
  { label: '电影/短片', value: 'movie' },
  { label: '电视剧集', value: 'series' }
] as const

type GalleryFilter = (typeof filterTabs)[number]['value']

/** Public gallery with project search and playable case details. */
export default function GalleryPage() {
  const router = useRouter()
  const { locale } = useCreatorLocale()
  const t = homeCopy[locale]
  const status = publicCopy[locale]
  const labels = filterLabels[locale]
  const token = useUserStore((state) => state.token)
  const createModal = useHomeShellCreateModal()
  const [searchQuery, setSearchQuery] = useState('')
  const [activeTab, setActiveTab] = useState<GalleryFilter>('all')
  const [works, setWorks] = useState<PublicCaseCardItem[]>([])
  const [loading, setLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState('')
  const requestGenerationRef = useRef(0)

  const projectType = useMemo<UserProjectType | undefined>(
    () => (activeTab === 'all' ? undefined : activeTab),
    [activeTab]
  )

  useEffect(() => {
    const generation = ++requestGenerationRef.current
    const timer = window.setTimeout(() => {
      setLoading(true)
      setErrorMessage('')
      void publicProjectVideoList({
        projectName: searchQuery.trim() || undefined,
        projectType,
        pageNum: 1,
        pageSize: 24
      })
        .then(({ rows }) => {
          if (generation !== requestGenerationRef.current) return
          setWorks(rows.map(normalizePublicCaseCard))
        })
        .catch(() => {
          if (generation !== requestGenerationRef.current) return
          setWorks([])
          setErrorMessage('案例列表加载失败，请稍后重试')
        })
        .finally(() => {
          if (generation === requestGenerationRef.current) setLoading(false)
        })
    }, searchQuery ? 250 : 0)

    return () => {
      window.clearTimeout(timer)
      if (requestGenerationRef.current === generation) requestGenerationRef.current += 1
    }
  }, [projectType, searchQuery])

  const startCreating = () => {
    if (!token) {
      requireLogin()
      return
    }
    createModal.openCreateModal()
  }

  const openCaseDetail = (projectId: number) => {
    router.push(`/case?id=${projectId}`)
  }

  return (
    <div className="sample-library" lang={locale} dir={locale === 'ar' ? 'rtl' : 'ltr'}>
      <header className="sample-library__intro">
        <h1>{t.examples}</h1>
        <p>{t.sampleLead}</p>
        <div className="sample-library__actions">
          <button type="button" onClick={startCreating}>{t.create}</button>
          <button type="button" onClick={() => router.push('/faq')}>{t.helpCenter}</button>
        </div>
      </header>

      <section aria-label={t.samples} className="sample-library__grid sample-library__films">
        {landingSamples.map((sample, index) => (
          <Link key={sample.id} href={`/case?sample=${sample.id}`} className="sample-library__card">
            <div className="sample-library__cover">
              <img src={withAppBasePath(sample.poster)} alt="" loading={index === 0 ? 'eager' : 'lazy'} />
            </div>
            <div className="sample-library__caption">
              <h2>{t.films[index][0]}</h2>
              <p>{t.films[index][1]}</p>
            </div>
          </Link>
        ))}
      </section>

      <section aria-labelledby="public-gallery-title">
        <h2 id="public-gallery-title">{t.gallery}</h2>
        <div className="sample-library__tools">
          <div className="sample-library__tabs" role="tablist" aria-label={t.gallery}>
            {filterTabs.map((tab, index) => (
              <button key={tab.value} type="button" role="tab" aria-selected={activeTab === tab.value}
                onClick={() => setActiveTab(tab.value)}>{labels[index]}</button>
            ))}
          </div>
          <label className="sample-library__search">
            <SearchOutlined />
            <input value={searchQuery} onChange={event => setSearchQuery(event.target.value)}
              type="search" placeholder={labels[3]} aria-label={labels[3]} />
          </label>
        </div>

        {loading && !works.length ? (
          <p role="status">{status.loading}</p>
        ) : errorMessage ? (
          <p role="alert">{status.failure}</p>
        ) : !works.length ? (
          <p role="status">{status.empty}</p>
        ) : (
          <div className="sample-library__grid" aria-busy={loading}>
            {works.map((work, index) => (
              <a key={work.id} className="sample-library__card" data-gallery-work-id={work.id}
                href={withAppBasePath(`/case?id=${work.id}`)}
                onClick={event => {
                  if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return
                  event.preventDefault()
                  openCaseDetail(work.id)
                }}>
                <div className="sample-library__cover">
                  <img src={work.coverUrl || emptyImageIconUrl} alt="" loading={index === 0 ? 'eager' : 'lazy'} />
                </div>
                <div className="sample-library__caption">
                  <h2>{work.title}</h2>
                  <p>{[work.category === 'series' ? labels[2] : labels[1], work.authorName, work.episodeCount > 0 && `${status.episode} · ${work.episodeCount}`].filter(Boolean).join(' · ')}</p>
                </div>
              </a>
            ))}
          </div>
        )}
      </section>
    </div>
  )
}
