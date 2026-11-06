import { describe, expect, it } from 'vitest'
import { resolveSkillRuntimeModelCode, runtimeResponseMode } from './storyScriptAgentRuntime'

describe('runtimeResponseMode', () => {
  it('does not demote an in-flight screenplay run when the snapshot briefly reports CHAT', () => {
    expect(runtimeResponseMode(
      { responseMode: 'CHAT', status: 'RUNNING', outputText: '' } as never,
      'SCREENPLAY'
    )).toBe('SCREENPLAY')
  })

  it('keeps succeeded screenplay output on SCREENPLAY even if handle.responseMode is CHAT', () => {
    const output = [
      '《末班车》',
      '',
      '1. 内景 地铁车厢 夜',
      '',
      '李明看着窗外。'
    ].join('\n')
    expect(runtimeResponseMode(
      {
        responseMode: 'CHAT',
        status: 'SUCCEEDED',
        outputText: output,
        assistantMessage: output
      } as never,
      'SCREENPLAY'
    )).toBe('SCREENPLAY')
  })

  it('keeps true chat replies as CHAT when there is no screenplay body', () => {
    expect(runtimeResponseMode(
      {
        responseMode: 'CHAT',
        status: 'SUCCEEDED',
        assistantMessage: '我是 AID 平台的专业编剧助手，我可以帮你创作剧本。'
      } as never,
      'SCREENPLAY'
    )).toBe('CHAT')
  })
})

describe('resolveSkillRuntimeModelCode', () => {
  const skill = {
    id: 1,
    skillCode: 'screenplay-create',
    defaultModelCode: 'deepseek-flash',
    models: [
      { modelCode: 'agnes-2.5-flash', modelName: 'Agnes 2.5 Flash' },
      { modelCode: 'deepseek-flash', modelName: 'DeepSeek Flash', defaultModel: true },
      { modelCode: 'deepseek-v4-pro', modelName: 'DeepSeek V4 Pro' }
    ]
  }

  it('keeps an explicitly selected model that the Skill allows', () => {
    expect(resolveSkillRuntimeModelCode(skill, 'deepseek-v4-pro')).toBe('deepseek-v4-pro')
  })

  it('falls back to the declared Skill default for an invalid or missing selection', () => {
    expect(resolveSkillRuntimeModelCode(skill, 'removed-model')).toBe('deepseek-flash')
    expect(resolveSkillRuntimeModelCode(skill)).toBe('deepseek-flash')
  })

  it('falls back to the model marked as default and then the first available model', () => {
    expect(resolveSkillRuntimeModelCode({
      ...skill,
      defaultModelCode: 'removed-model'
    })).toBe('deepseek-flash')
    expect(resolveSkillRuntimeModelCode({
      ...skill,
      defaultModelCode: null,
      models: skill.models.map((model) => ({ ...model, defaultModel: false }))
    })).toBe('agnes-2.5-flash')
  })
})
