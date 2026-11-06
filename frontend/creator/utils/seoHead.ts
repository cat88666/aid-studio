import { creatorInterfaceText } from '~/config/creatorInterfaceText'
import type { CreatorLocale } from '~/config/creatorLanguages'
import { request } from '~/utils/api'
import { CREATOR_BRAND_KEYWORDS, creatorBrandName } from '~/config/creatorBrand'

export interface PublicSeoMeta {
  title?: string
  description?: string
  keywords?: string
  canonicalUrl?: string
  imageUrl?: string
  robots?: string
}

const PAGE_TITLES: Record<string, [string, string]> = {
  '/works': ['My projects', '我的作品'],
  '/gallery': ['Gallery', '公开作品'],
  '/assets': ['Assets', '资产库'],
  '/billing': ['Billing', '账单明细'],
  '/invite': ['Invitations', '邀请管理'],
  '/faq': ['Help', '常见问题'],
  '/about': ['About', '关于我们'],
  '/login': ['Sign in', '登录'],
  '/forgot-password': ['Reset password', '找回密码'],
  '/case': ['Project details', '作品详情'],
  '/mobile': ['Mobile access', '移动端提示'],
  '/create': ['Studio', '创作工作台'],
  '/create/studio': ['Storyboard studio', '分镜工作台'],
  '/create/global-setting': ['Project settings', '项目设置'],
  '/create/story-script': ['Script', '剧本创作'],
  '/create/series-episode-list': ['Episodes', '剧集目录'],
  '/create/series-script-upload': ['Import script', '导入剧本'],
  '/create/scene-character': ['Scenes and characters', '场景与角色'],
  '/create/storyboard-script': ['Storyboard', '分镜脚本'],
  '/create/storyboard-video': ['Video shots', '分镜视频'],
  '/create/dubbing': ['Voiceover', '角色配音'],
  '/create/preview': ['Preview', '成片预览']
}

export function normalizeSeoPath(pathname: string): string {
  return pathname.replace(/\/+$/, '') || '/'
}

export function isPrivateSeoPath(pathname: string): boolean {
  return /^\/(?:admin|assets|billing|create|forgot-password|login|user|works|invite|mobile|index-legacy|case)(?:\/|$)/.test(pathname)
}

/** 品牌与头图由定制端维护；页面级内容、索引策略与规范地址沿用登记配置。 */
export function resolveSiteSeo(pathname: string, registered?: PublicSeoMeta | null, locale: CreatorLocale = 'en') {
  const route = normalizeSeoPath(pathname)
  const name = creatorBrandName(locale)
  const text = creatorInterfaceText[locale]
  const localizedTitles: Record<string, string> = { '/login': text.login, '/forgot-password': text.reset, '/works': text.projects }
  const pageTitle = localizedTitles[route] || PAGE_TITLES[route]?.[locale === 'zh' ? 1 : 0]
  const isPrivate = isPrivateSeoPath(route)
  const publicMeta = isPrivate ? null : registered
  const description = text.description
  return {
    title: pageTitle ? pageTitle + ' - ' + name : name,
    description: (locale === 'zh' && publicMeta?.description?.replace(/视觉·AID|AID Studio|镜序/gi, name)) || description,
    keywords: (locale === 'zh' && publicMeta?.keywords?.replace(/视觉·AID|AID Studio|镜序/gi, name)) || CREATOR_BRAND_KEYWORDS,
    robots: isPrivate ? 'noindex,nofollow' : publicMeta?.robots || 'index,follow',
    canonicalUrl: publicMeta?.canonicalUrl
  }
}

const metaInflight = new Map<string, Promise<PublicSeoMeta | null>>()
const metaCache = new Map<string, { value: PublicSeoMeta | null; at: number }>()
const META_CACHE_MS = 60_000

/** 未登记路径返回 404，表示无覆盖配置；按路径合并请求并做一分钟短时缓存。 */
export function loadRegisteredSeoMeta(path: string, force = false): Promise<PublicSeoMeta | null> {
  const normalized = normalizeSeoPath(path.startsWith('/') ? path : `/${path}`)
  const running = metaInflight.get(normalized)
  if (running) return running
  const cached = metaCache.get(normalized)
  if (!force && cached && Date.now() - cached.at < META_CACHE_MS) return Promise.resolve(cached.value)
  const promise = request
    .get<PublicSeoMeta>('/seo/public/meta', { path: normalized }, {
      validateStatus: (status) => (status >= 200 && status < 300) || status === 404
    })
    .then((response) => response || null)
    .catch(() => null)
    .then((value) => {
      metaCache.set(normalized, { value, at: Date.now() })
      return value
    })
    .finally(() => metaInflight.delete(normalized))
  metaInflight.set(normalized, promise)
  return promise
}
