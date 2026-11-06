'use client'

import { useSyncExternalStore } from 'react'
import { usePathname } from 'next/navigation'
import { creatorLanguages, creatorLanguage, languageFromPath, type CreatorLocale } from '~/config/creatorLanguages'
import { withAppBasePath } from '~/utils/appBasePath'
export type { CreatorLocale } from '~/config/creatorLanguages'

const preferenceKey = 'creator.locale'
const changeEvent = 'creator:locale-change'
let unsavedPreference: CreatorLocale | undefined

export function readCreatorPreference(): CreatorLocale {
  let saved: string | null = null
  try { saved = localStorage.getItem(preferenceKey) } catch { /* Storage is optional. */ }
  if (unsavedPreference) return unsavedPreference
  const preference = creatorLanguages.find(language => language.code === saved)
  if (preference) return preference.code
  return 'ja'
}

export function rememberCreatorLocale(locale: CreatorLocale) {
  try {
    localStorage.setItem(preferenceKey, locale)
    unsavedPreference = undefined
  } catch { unsavedPreference = locale }
  window.dispatchEvent(new Event(changeEvent))
}

function setLocale(locale: CreatorLocale) {
  rememberCreatorLocale(locale)
  const url = withAppBasePath(creatorLanguage(locale).path)
  if (window.location.pathname !== url) window.location.assign(url + window.location.search + window.location.hash)
}

function subscribe(callback: () => void) {
  window.addEventListener(changeEvent, callback)
  window.addEventListener('storage', callback)
  return () => {
    window.removeEventListener(changeEvent, callback)
    window.removeEventListener('storage', callback)
  }
}

const initialLocale = (): CreatorLocale => 'ja'
export function useCreatorLocale() {
  const pathname = usePathname() || '/'
  const preference = useSyncExternalStore(subscribe, readCreatorPreference, initialLocale)
  return { locale: pathname === '/' ? preference : languageFromPath(pathname)?.code || preference, setLocale }
}
