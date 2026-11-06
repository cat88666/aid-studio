'use client'

import { useSyncExternalStore } from 'react'

export type CreatorLocale = 'en' | 'zh'
const preferenceKey = 'creator.locale'
const changeEvent = 'creator:locale-change'
let unsavedPreference: CreatorLocale | undefined

function readLocale(): CreatorLocale {
  let saved: string | null = null
  try { saved = localStorage.getItem(preferenceKey) } catch { /* Storage is optional. */ }
  if (unsavedPreference) return unsavedPreference
  if (saved === 'en' || saved === 'zh') return saved
  return /^zh(?:-|$)/i.test(navigator.language) ? 'zh' : 'en'
}

function setLocale(locale: CreatorLocale) {
  try {
    localStorage.setItem(preferenceKey, locale)
    unsavedPreference = undefined
  } catch {
    unsavedPreference = locale
  }
  window.dispatchEvent(new Event(changeEvent))
}

function subscribe(callback: () => void) {
  window.addEventListener(changeEvent, callback)
  window.addEventListener('storage', callback)
  return () => {
    window.removeEventListener(changeEvent, callback)
    window.removeEventListener('storage', callback)
  }
}

const initialLocale = (): CreatorLocale => 'en'

export function useCreatorLocale() {
  const locale = useSyncExternalStore(subscribe, readLocale, initialLocale)
  return { locale, setLocale }
}
