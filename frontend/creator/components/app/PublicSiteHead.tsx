'use client'

import { usePathname } from 'next/navigation'
import { useEffect, useState, useSyncExternalStore } from 'react'
import { CREATOR_BRAND_ICON, CREATOR_BRAND_NAME } from '~/config/creatorBrand'
import { withAppBasePath } from '~/utils/appBasePath'
import { isPrivateSeoPath, loadRegisteredSeoMeta, normalizeSeoPath, resolveSiteSeo, type PublicSeoMeta } from '~/utils/seoHead'

const subscribeToOrigin = () => () => {}
const getClientOrigin = () => window.location.origin
const getServerOrigin = () => ''

/** 定制 C 端的页面标签独立于同服作者版的 SEO 与品牌设置。 */
export function PublicSiteHead() {
  const pathname = usePathname() || '/'
  const origin = useSyncExternalStore(subscribeToOrigin, getClientOrigin, getServerOrigin)
  const route = normalizeSeoPath(pathname)
  const [registered, setRegistered] = useState<{ route: string; value: PublicSeoMeta | null } | null>(null)
  useEffect(() => {
    if (isPrivateSeoPath(route)) return
    let active = true
    void loadRegisteredSeoMeta(route).then(value => {
      if (active) setRegistered({ route, value })
    })
    return () => { active = false }
  }, [route])

  const seo = resolveSiteSeo(route, registered?.route === route ? registered.value : null)
  const canonicalUrl = seo.canonicalUrl || (origin ? origin + withAppBasePath(pathname) : undefined)

  return (
    <>
      <title>{seo.title}</title>
      <meta name="description" content={seo.description} />
      <meta name="keywords" content={seo.keywords} />
      <meta name="robots" content={seo.robots} />
      <meta property="og:site_name" content={CREATOR_BRAND_NAME} />
      <meta property="og:title" content={seo.title} />
      <meta property="og:description" content={seo.description} />
      <meta property="og:type" content="website" />
      <meta property="og:locale" content="zh_CN" />
      {canonicalUrl && <meta property="og:url" content={canonicalUrl} />}
      <meta name="twitter:card" content="summary" />
      <meta name="twitter:title" content={seo.title} />
      <meta name="twitter:description" content={seo.description} />
      {canonicalUrl && <link rel="canonical" href={canonicalUrl} />}
      <link rel="icon" href={CREATOR_BRAND_ICON} type="image/svg+xml" />
      <link rel="apple-touch-icon" href={withAppBasePath('/brand/jingxu-touch.png')} />
    </>
  )
}
