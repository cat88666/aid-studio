import type { CreatorLocale } from './creatorLanguages'
import { withAppBasePath } from '~/utils/appBasePath'

export const CREATOR_BRAND_NAME = 'Sakura AGIC'
export function creatorBrandName(locale: CreatorLocale) {
  if (locale === 'zh-hant') return '櫻花 AGIC'
  return locale === 'zh' ? '樱花 AGIC' : CREATOR_BRAND_NAME
}

export const CREATOR_BRAND_DESCRIPTION = 'Create short films with AI, from your first script and storyboard to the final cut.'
export const CREATOR_BRAND_KEYWORDS = 'Sakura AGIC,AI filmmaking,storyboards,short films'
export const CREATOR_BRAND_ICON = withAppBasePath('/brand/agic.svg')
