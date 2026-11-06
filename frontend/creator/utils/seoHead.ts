import { request } from '~/utils/api'
import { CREATOR_BRAND_DESCRIPTION, CREATOR_BRAND_KEYWORDS, CREATOR_BRAND_NAME } from '~/config/creatorBrand'

export interface PublicSeoMeta {
  title?: string
  description?: string
  keywords?: string
  canonicalUrl?: string
  imageUrl?: string
  robots?: string
}

const PAGE_TITLES: Record<string, string> = {
  '/works': '我的作品',
  '/gallery': '公开作品',
  '/assets': '资产库',
  '/billing': '账单明细',
  '/invite': '邀请管理',
  '/faq': '常见问题',
  '/about': '关于我们',
  '/login': '登录',
  '/forgot-password': '找回密码',
  '/case': '作品详情',
  '/mobile': '移动端提示',
  '/create': '创作工作台',
  '/create/studio': '分镜工作台',
  '/create/global-setting': '项目设置',
  '/create/story-script': '剧本创作',
  '/create/series-episode-list': '剧集目录',
  '/create/series-script-upload': '导入剧本',
  '/create/scene-character': '场景与角色',
  '/create/storyboard-script': '分镜脚本',
  '/create/storyboard-video': '分镜视频',
  '/create/dubbing': '角色配音',
  '/create/preview': '成片预览'
}

export function normalizeSeoPath(pathname: string): string {
  return pathname.replace(/\/+$/, '') || '/'
}

export function isPrivateSeoPath(pathname: string): boolean {
  return /^\/(?:admin|assets|billing|create|forgot-password|login|user|works|invite|mobile|index-legacy|case)(?:\/|$)/.test(pathname)
}

/** 品牌与头图由定制端维护；页面级内容、索引策略与规范地址沿用登记配置。 */
export function resolveSiteSeo(pathname: string, registered?: PublicSeoMeta | null) {
  const route = normalizeSeoPath(pathname)
  const pageTitle = PAGE_TITLES[route]
  const isPrivate = isPrivateSeoPath(route)
  const publicMeta = isPrivate ? null : registered
  const description = route === '/faq' ? '产品使用说明、常见问题与帮助中心'
    : route === '/about' ? '了解产品、服务与创作平台' : CREATOR_BRAND_DESCRIPTION
  return {
    title: pageTitle ? pageTitle + ' - ' + CREATOR_BRAND_NAME : CREATOR_BRAND_NAME,
    description: publicMeta?.description?.replace(/视觉·AID|AID Studio/gi, CREATOR_BRAND_NAME) || description,
    keywords: publicMeta?.keywords?.replace(/视觉·AID|AID Studio/gi, CREATOR_BRAND_NAME) || CREATOR_BRAND_KEYWORDS,
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
