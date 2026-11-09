'use client'

import { useCreatorLocale } from '~/hooks/useCreatorLocale'

/** Translate interface copy only; project content and model identifiers stay untouched. */
export function useCreateCopy() {
  const { locale } = useCreatorLocale()
  const chinese = locale === 'zh' || locale === 'zh-hant'
  return (zh: string, en: string) => chinese ? zh : en
}
