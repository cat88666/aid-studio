// @vitest-environment jsdom
import { beforeEach, expect, it } from 'vitest'
import { migrateSakuraStorage } from './sakuraStorageMigration'
import { normalizeSakuraProtocol } from './sakuraProtocol'

beforeEach(() => {
  localStorage.clear()
  sessionStorage.clear()
})

it('moves drafts without changing content or unrelated keys', () => {
  localStorage.setItem('aid:story-script:unsynced:1:2', 'AID is a character in this draft')
  localStorage.setItem('paid:invoice', 'paid')
  sessionStorage.setItem('aid:home-gallery-scroll:v1', '42')
  migrateSakuraStorage()
  expect(localStorage.getItem('sakura:story-script:unsynced:1:2')).toBe('AID is a character in this draft')
  expect(localStorage.getItem('aid:story-script:unsynced:1:2')).toBeNull()
  expect(localStorage.getItem('paid:invoice')).toBe('paid')
  expect(sessionStorage.getItem('sakura:home-gallery-scroll:v1')).toBe('42')
})

it('keeps both conflicting drafts and is idempotent', () => {
  localStorage.setItem('aid:story-script:unsynced:1:2', 'old tab draft')
  localStorage.setItem('sakura:story-script:unsynced:1:2', 'new tab draft')
  migrateSakuraStorage()
  migrateSakuraStorage()
  expect(localStorage.getItem('sakura:story-script:unsynced:1:2')).toBe('new tab draft')
  expect(localStorage.getItem('sakura:legacy-backup:localStorage::story-script:unsynced:1:2')).toBe('old tab draft')
})

it('normalizes known envelopes while leaving free text untouched', () => {
  expect(normalizeSakuraProtocol('[[AID_SCRIPT_COMMENT_V1]]paid AID[[/AID_SCRIPT_COMMENT_V1]]'))
    .toBe('[[SAKURA_SCRIPT_COMMENT_V1]]paid AID[[/SAKURA_SCRIPT_COMMENT_V1]]')
  localStorage.setItem('aid-story-script-agent:v3:1:2', '{"prompt":"[AID_MODE:SCREENPLAY]"}')
  migrateSakuraStorage()
  expect(localStorage.getItem('sakura-story-script-agent:v3:1:2')).toBe('{"prompt":"[SAKURA_MODE:SCREENPLAY]"}')
})
