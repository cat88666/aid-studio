import { describe, expect, it } from 'vitest'
import { creatorLanguages } from '~/config/creatorLanguages'
import { sampleCopy, sampleLibrary } from './sampleLibrary'

describe('public sample library', () => {
  it('has 24 independently addressable videos', () => {
    expect(sampleLibrary).toHaveLength(24)
    expect(new Set(sampleLibrary.map(sample => sample.id)).size).toBe(24)
    expect(new Set(sampleLibrary.map(sample => sample.video)).size).toBe(24)
  })

  it('resolves every video title in every supported language', () => {
    for (const language of creatorLanguages) {
      for (const sample of sampleLibrary) {
        const [title, description] = sampleCopy(sample.id, language.code)
        expect(title).toBeTruthy()
        expect(title).not.toBe(sample.id)
        expect(description).toBeTruthy()
      }
    }
  })
})
