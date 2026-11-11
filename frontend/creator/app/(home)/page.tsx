'use client'

import { Suspense, useEffect } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import CreatorLanding from '~/components/home/CreatorLanding'
import { creatorLanguage } from '~/config/creatorLanguages'
import { useCreatorLocale } from '~/hooks/useCreatorLocale'

function LegacyProjectsRedirect() {
  const query = useSearchParams()
  const router = useRouter()
  const showProjects = query.get('projects') === '1'
  useEffect(() => {
    if (showProjects) router.replace('/projects/')
  }, [router, showProjects])
  return null
}

export default function CreatorHomePage() {
  const { locale } = useCreatorLocale()
  return <div className="creator-landing" lang={creatorLanguage(locale).tag} dir={locale === 'ar' ? 'rtl' : 'ltr'}>
    <CreatorLanding />
    <Suspense fallback={null}><LegacyProjectsRedirect /></Suspense>
  </div>
}
