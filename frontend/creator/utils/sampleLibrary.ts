import type { CreatorLocale } from '~/config/creatorLanguages'
import { homeCopy } from '~/config/home-locales'
import labels from '~/config/sampleLibraryLabels.json'
import { landingSamples } from './landingSamples'

export const sampleSource = {
  name: 'Pyramid Flow · Yang Jin et al.',
  url: 'https://pyramid-flow.github.io/',
  license: 'CC BY-SA 4.0',
  licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0/'
}

const modelIds = ['tokyo', 'monster', 'dusk', 'chicken', 'eiffel', 'waking', 'church', 'fireworks', 'waves', 'trailer', 'lighthouse', 'hikers', 'rail', 'wall', 'campfire', 'sunday', 'tsunami', 'explosion'] as const

const displayOrder = ['neon', 'tokyo', 'monster', 'hero', 'dusk', 'chicken', 'ink', 'eiffel', 'waking', 'church', 'cinema', 'fireworks', 'waves', 'adventure', 'trailer', 'lighthouse', 'hikers', 'rail', 'wall', 'meadow', 'campfire', 'sunday', 'tsunami', 'explosion']

export const sampleLibrary = [
  ...landingSamples.map(sample => ({ ...sample, category: 'films' as const })),
  ...modelIds.map(id => ({ id, category: 'models' as const, poster: `/creator-home/library/${id}.webp`, video: `/creator-home/library/${id}.mp4` }))
].sort((a, b) => displayOrder.indexOf(a.id) - displayOrder.indexOf(b.id))

export function sampleCopy(id: string, locale: CreatorLocale): [string, string] {
  const index = landingSamples.findIndex(sample => sample.id === id)
  if (index >= 0) return [...homeCopy[locale].films[index]] as [string, string]
  const titles = labels[locale].titles as Record<string, string>
  return [titles[id] || id, `${labels[locale].models} · Pyramid Flow`]
}
