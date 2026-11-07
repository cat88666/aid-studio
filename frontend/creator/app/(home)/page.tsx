'use client'

import { Suspense } from 'react'
import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import CreatorLanding from '~/components/home/CreatorLanding'
import DashboardProjects from '~/components/home/DashboardProjects'
import { creatorLanguage } from '~/config/creatorLanguages'
import { useCreatorLocale } from '~/hooks/useCreatorLocale'

function ProjectOverlay() {
  const query = useSearchParams()
  const pathname = usePathname()
  const router = useRouter()
  if (query.get('projects') !== '1') return null
  function closeProjects() {
    const remaining = new URLSearchParams(query.toString())
    remaining.delete('projects')
    router.replace(pathname + (remaining.size ? '?' + remaining : ''), { scroll: false })
  }
  return <DashboardProjects onClose={closeProjects} />
}

export default function CreatorHomePage() {
  const { locale } = useCreatorLocale()
  return <div className="creator-landing" lang={creatorLanguage(locale).tag} dir={locale === 'ar' ? 'rtl' : 'ltr'}>
    <CreatorLanding />
    <Suspense fallback={null}><ProjectOverlay /></Suspense>
  </div>
}
