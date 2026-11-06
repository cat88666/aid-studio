import { describe, expect, it } from 'vitest'
import { bindRuntimeMessages, historyRunMessages, mergeHydratedMessages } from './storyScriptAgentMessages'
import { resolveStoryScriptAgentDocumentView } from './storyScriptAgentDocumentView'
import type { UserSkillRuntimeRunHandle } from '~/types/user-skill'
import type { StoryScriptAgentViewMessage } from '~/hooks/useStoryScriptAgent'

function run(status: string): UserSkillRuntimeRunHandle {
  return { runId: 42, skillCode: 'screenplay', skillVersionId: 20, generation: 1,
    status, stage: 'PLANNING', operation: 'AUTO', qualityMode: 'AUTO', tasks: [], prompt: '小狗乱叫的剧本' }
}
function live(): StoryScriptAgentViewMessage[] {
  return [
    { id: 'user-web-new', role: 'user', content: '小狗乱叫的剧本', status: 'complete' },
    { id: 'assistant-web-new', role: 'assistant', content: '正在生成的正文', status: 'streaming' }
  ]
}

describe('Skill history and invoke response ordering', () => {
  it.each(['CREATED', 'RUNNING', 'CANCELING', 'UNKNOWN'])('does not label %s as failure', (status) => {
    expect(historyRunMessages(run(status))[1]).toMatchObject({ status: 'streaming', content: '' })
  })
  it('keeps waiting separate from failure', () => {
    expect(historyRunMessages(run('NEEDS_INPUT'))[1]).toMatchObject({ status: 'complete', content: '' })
  })
  it('restores the input request needed to continue a waiting run', () => {
    const requiredInput = { runId: 42, requestId: 7, round: 1, contextVersion: 'c1', schemaDigest: 's1',
      confirmedFacts: [], assumptions: [], questions: [{ id: 'duration', field: 'duration',
        required: true, question: '时长？', inputType: 'number' as const, options: [], allowCustom: true, allowAiDecide: true }] }
    expect(historyRunMessages({ ...run('NEEDS_INPUT'), requiredInput })[1].inputRequest)
      .toMatchObject({ requestId: 7, questions: [{ id: 'duration' }] })
  })
  it('retains genuine failure, cancellation and success', () => {
    expect(historyRunMessages({ ...run('FAILED'), errorMessage: '上游正文为空' })[1])
      .toMatchObject({ status: 'error', content: '上游正文为空' })
    expect(historyRunMessages(run('FAILED'))[1].content).toBe('Skill 运行失败')
    expect(historyRunMessages(run('CANCELED'))[1].status).toBe('stopped')
    expect(historyRunMessages({ ...run('SUCCEEDED'), outputText: '完整正文' })[1])
      .toMatchObject({ status: 'complete', content: '完整正文', partialOutputTrusted: true })
  })
  it.each(['history-first', 'invoke-first'])('reconciles %s without duplicates or lost deltas', (order) => {
    const history = historyRunMessages(run('RUNNING'))
    const messages = order === 'history-first'
      ? bindRuntimeMessages(mergeHydratedMessages(history, live()), 'web-new', 42)
      : mergeHydratedMessages(history, bindRuntimeMessages(live(), 'web-new', 42))
    expect(messages).toHaveLength(2)
    expect(messages[1]).toMatchObject({ id: 'assistant-web-new', runId: 42, content: '正在生成的正文', status: 'streaming' })
    const completed = messages.map((m) => m.role === 'assistant'
      ? { ...m, status: 'complete' as const, content: '完整正文' } : m)
    expect(mergeHydratedMessages(history, completed)).toEqual(completed)
    expect(bindRuntimeMessages(completed, 'web-new', 42)).toEqual(completed)
  })
  it('keeps separate runs even when prompts match', () => {
    const old = historyRunMessages({ ...run('SUCCEEDED'), runId: 41 })
    const messages = bindRuntimeMessages(mergeHydratedMessages(old, live()), 'web-new', 42)
    expect(messages).toHaveLength(4)
    expect(messages.map((m) => m.runId)).toEqual([41, 41, 42, 42])
  })
  it('does not advertise applying failed content', () => {
    const view = resolveStoryScriptAgentDocumentView({ status: 'error', content: '上游正文为空' })
    expect(view.showActions).toBe(false)
    expect(view.subtitle).not.toContain('带入')
  })
})
