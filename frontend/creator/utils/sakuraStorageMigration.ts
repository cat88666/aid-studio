import { normalizeSakuraProtocol } from './sakuraProtocol'

/** Move only this application's legacy keys; preserve draft contents. */
const LEGACY_PREFIXES = [
  'aid:story-script:unsynced:',
  'aid:video-frames:v1:',
  'aid:home-gallery-scroll:v1',
  'aid.scriptExtractBaseline.v1',
  'aid.scriptExtractIgnored.v1',
  'aid-story-script-agent:v2:',
  'aid-story-script-agent:v3:',
  'aid-studio-flow-layout:v1:'
] as const
let retiredPosterCache = false

export function migrateSakuraStorage(): void {
  if (typeof window === 'undefined') return
  if (!retiredPosterCache && typeof indexedDB !== 'undefined') {
    try {
      // Derived thumbnails regenerate in sakura-video-poster-cache; no user data lives here.
      indexedDB.deleteDatabase('aid-video-poster-cache')
      retiredPosterCache = true
    } catch {
      // Private browsing can disable IndexedDB.
    }
  }
  for (const name of ['localStorage', 'sessionStorage'] as const) {
    try {
      const storage = window[name]
      for (let i = storage.length - 1; i >= 0; i -= 1) {
        const key = storage.key(i)
        if (!key || !LEGACY_PREFIXES.some((prefix) => key.startsWith(prefix))) continue
        const stored = storage.getItem(key)
        if (stored === null) continue
        const value = key.startsWith('aid-story-script-agent:') ? normalizeSakuraProtocol(stored) : stored
        const nextKey = `sakura${key.slice(3)}`
        const existing = storage.getItem(nextKey)
        if (existing === null) storage.setItem(nextKey, value)
        else if (existing !== value) {
          // Keep both copies if an older tab wrote a different unsynced draft.
          storage.setItem(`sakura:legacy-backup:${name}:${key.slice(3)}`, value)
        }
        storage.removeItem(key)
      }
    } catch {
      // Storage can be unavailable or full. Never remove a draft if copying failed.
    }
  }
}

// Run before importing components restore their browser state.
migrateSakuraStorage()
