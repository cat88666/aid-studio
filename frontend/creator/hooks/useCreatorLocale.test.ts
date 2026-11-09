import { afterEach, describe, expect, it, vi } from 'vitest'
import { creatorLanguages, languageFromPath } from '../config/creatorLanguages'
import { readCreatorPreference } from './useCreatorLocale'

afterEach(() => vi.unstubAllGlobals())

describe('creator language selection', () => {
  it('serves English at the root and keeps each explicit language link distinct', () => {
    expect(languageFromPath('/')?.code).toBe('en')
    expect(languageFromPath('/en')?.code).toBe('en')
    expect(languageFromPath('/ja/')?.code).toBe('ja')
    for (const language of creatorLanguages) {
      expect(languageFromPath(language.path)?.code).toBe(language.code)
    }
    expect(languageFromPath('/works/')).toBeUndefined()
  })

  it.each([null, 'unknown'])('defaults to English with preference %s in an English browser', saved => {
    vi.stubGlobal('navigator', { language: 'en-US', languages: ['en-US'] })
    vi.stubGlobal('localStorage', { getItem: () => saved })
    expect(readCreatorPreference()).toBe('en')
  })

  it('keeps a previously selected language on workspace pages', () => {
    vi.stubGlobal('localStorage', { getItem: () => 'zh-hant' })
    expect(readCreatorPreference()).toBe('zh-hant')
  })
})
