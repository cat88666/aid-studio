import type { StoryScriptAgentViewMessage } from '~/hooks/useStoryScriptAgent'
import type { UserSkillRuntimeRunHandle } from '~/types/user-skill'
import { parseStoryScriptAgentPrompt } from './storyScriptAgentReference'
import { normalizeUserSkillInputRequest } from './storyScriptAgentClarification'
import { runtimeResponseMode } from './storyScriptAgentRuntime'

export function historyRunMessages(handle: UserSkillRuntimeRunHandle): StoryScriptAgentViewMessage[] {
  const rawPrompt = String(handle.prompt || '').trim()
  const parsed = parseStoryScriptAgentPrompt(rawPrompt)
  const failed = handle.status === 'FAILED'
  const canceled = handle.status === 'CANCELED'
  const succeeded = handle.status === 'SUCCEEDED'
  const waiting = handle.status === 'NEEDS_INPUT'
  return [
    {
      id: `user-run-${handle.runId}`, role: 'user', runId: handle.runId,
      content: parsed.instruction || rawPrompt, status: 'complete', references: parsed.references
    },
    {
      id: `assistant-run-${handle.runId}`, role: 'assistant', runId: handle.runId,
      content: failed ? String(handle.errorMessage || 'Skill 运行失败')
        : canceled ? String(handle.errorMessage || '生成已停止')
          : String(handle.assistantMessage || handle.outputText || handle.reviewReport || ''),
      status: failed ? 'error' : canceled ? 'stopped' : succeeded || waiting ? 'complete' : 'streaming',
      responseMode: runtimeResponseMode(handle, 'SCREENPLAY'),
      references: parsed.references,
      inputRequest: waiting ? normalizeUserSkillInputRequest(handle.requiredInput, handle.runId) ?? undefined : undefined,
      partialOutputTrusted: succeeded
    }
  ]
}

function identity(message: StoryScriptAgentViewMessage): string {
  return message.runId ? `${message.role}:run:${message.runId}` : `${message.role}:id:${message.id}`
}

/** Keep live message IDs and deltas authoritative over older history snapshots. */
export function mergeHydratedMessages(
  history: StoryScriptAgentViewMessage[], current: StoryScriptAgentViewMessage[]
): StoryScriptAgentViewMessage[] {
  const merged = new Map<string, StoryScriptAgentViewMessage>()
  for (const message of [...history, ...current]) merged.set(identity(message), message)
  return [...merged.values()]
}

/** An invoke response can arrive after history has already discovered the same Run. */
export function bindRuntimeMessages(
  messages: StoryScriptAgentViewMessage[], idempotencyKey: string, runId: number
): StoryScriptAgentViewMessage[] {
  const liveIds = new Set([`user-${idempotencyKey}`, `assistant-${idempotencyKey}`])
  const live = messages.filter((message) => liveIds.has(message.id)).map((message) => ({ ...message, runId }))
  return mergeHydratedMessages(messages.filter((message) => !liveIds.has(message.id)), live)
}
