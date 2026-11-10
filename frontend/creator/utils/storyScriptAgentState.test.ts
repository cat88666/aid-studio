// @vitest-environment jsdom

import { beforeEach, describe, expect, it } from 'vitest'
import type { StoryScriptAgentProjectState } from './storyScriptAgentState'
import { readStoryScriptAgentState, writeStoryScriptAgentState } from './storyScriptAgentState'

function state(projectId: number, episodeId: number, runId: number): StoryScriptAgentProjectState {
  const idempotencyKey = `web-${runId}`
  return {
    version: 3,
    projectId,
    episodeId,
    skill: { id: 1, skillCode: 'screenplay' },
    modelCode: 'deepseek-v4-pro',
    autoOpen: false,
    activeRun: {
      idempotencyKey,
      invokeRequest: {
        skillCode: 'screenplay',
        modelCode: 'deepseek-v4-pro',
        idempotencyKey,
        projectId,
        episodeId,
        operation: 'CREATE',
        qualityMode: 'NORMAL'
      },
      prompt: '生成剧本',
      references: [],
      responseMode: 'SCREENPLAY',
      runId,
      afterSeq: 0,
      partialOutputTrusted: true
    },
    updatedAt: Date.now()
  }
}

describe('storyScriptAgentState runtime scope', () => {
  beforeEach(() => {
    sessionStorage.clear()
    localStorage.clear()
    localStorage.setItem('user-info', JSON.stringify({ id: '9007199254740993123', identityVersion: 'public-snowflake-v1' }))
  })

  it('isolates Runtime checkpoints by project and episode', () => {
    expect(writeStoryScriptAgentState(state(10, 101, 1001))).toBe(true)
    expect(writeStoryScriptAgentState(state(10, 102, 1002))).toBe(true)
    expect(writeStoryScriptAgentState(state(11, 0, 1003))).toBe(true)

    expect(readStoryScriptAgentState(10, 101)?.activeRun?.runId).toBe(1001)
    expect(readStoryScriptAgentState(10, 102)?.activeRun?.runId).toBe(1002)
    expect(readStoryScriptAgentState(11, 0)?.activeRun?.runId).toBe(1003)
    expect(readStoryScriptAgentState(10, 0)).toBeNull()
  })

  it('removes incompatible v2 session state', () => {
    localStorage.setItem('sakura-story-script-agent:v2:10:0', JSON.stringify({ version: 2 }))
    expect(readStoryScriptAgentState(10, 0)).toBeNull()
    expect(localStorage.getItem('sakura-story-script-agent:v2:10:0')).toBeNull()
  })

  it('preserves old-owner drafts without rebinding them to a new public identity', () => {
    const old = { ...state(10, 0, 1001), ownerId: '3' }
    const raw = JSON.stringify(old)
    const key = 'sakura-story-script-agent:v3:10:0'
    localStorage.setItem(key, raw)
    expect(readStoryScriptAgentState(10, 0)).toBeNull()
    expect(localStorage.getItem(key)).toBe(raw)
    expect(writeStoryScriptAgentState(old)).toBe(false)
    expect(writeStoryScriptAgentState(state(10, 0, 2001))).toBe(true)
    const backups = Object.keys(localStorage).filter((k) => k.startsWith('sakura-story-script-preserved:'))
    expect(backups.some((k) => localStorage.getItem(k) === raw)).toBe(true)
    expect(readStoryScriptAgentState(10, 0)?.ownerId).toBe('9007199254740993123')
  })

  it('does not restore or reassign a draft while only a legacy user cache is available', () => {
    localStorage.setItem('user-info', JSON.stringify({ id: '3', nickName: 'Sakura_abc123' }))
    const raw = JSON.stringify({ ...state(10, 0, 1001), ownerId: '3' })
    localStorage.setItem('sakura-story-script-agent:v3:10:0', raw)
    expect(readStoryScriptAgentState(10, 0)).toBeNull()
    expect(writeStoryScriptAgentState(state(10, 0, 2001))).toBe(false)
    expect(localStorage.getItem('sakura-story-script-agent:v3:10:0')).toBe(raw)
  })

  it('keeps a full checkpoint while the invoke response has no known run id', () => {
    const pending = state(12, 0, 1200)
    pending.activeRun = {
      ...pending.activeRun!,
      runId: null,
      partialOutputTrusted: false
    }

    expect(writeStoryScriptAgentState(pending)).toBe(true)
    const restored = readStoryScriptAgentState(12, 0)?.activeRun
    expect(restored?.runId).toBeNull()
    expect(restored?.idempotencyKey).toBe('web-1200')
    expect(restored?.invokeRequest.idempotencyKey).toBe('web-1200')
    expect(restored?.invokeRequest.modelCode).toBe('deepseek-v4-pro')
    expect(restored?.partialOutputTrusted).toBe(false)
  })
})
