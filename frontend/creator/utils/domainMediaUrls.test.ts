import { describe, expect, it } from 'vitest'
import { normalizeDomainMediaUrls } from './domainMediaUrls'

describe('domain media URLs', () => {
  it('normalizes nested legacy media while preserving other URLs and body text', () => {
    const data = { media: ['/profile/a.png', 'https://43.154.247.11:8848/profile/b.mp4'], remote: 'https://other.test/profile/a', text: 'reference /profile/a' }
    expect(normalizeDomainMediaUrls(data)).toEqual({ media: ['https://thdrhdf.site/profile/a.png', 'https://thdrhdf.site/profile/b.mp4'], remote: data.remote, text: data.text })
    expect(data.media[0]).toBe('/profile/a.png')
  })
})
