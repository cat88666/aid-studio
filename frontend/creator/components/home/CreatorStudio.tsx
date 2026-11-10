'use client'

import { useEffect, useRef, useState } from 'react'
import CreatorDashboard from './CreatorDashboard'
import { useCreatorLocale } from '~/hooks/useCreatorLocale'
import { useUserStore } from '~/stores/user'
import { clearAuthSessionArtifacts, requireLogin } from '~/utils/authLoginNavigation'
import { buildUserApiAuthHeaders, resolveClientApiUrl } from '~/utils/api'
import { parseSseEventBlock } from '~/utils/sseEventBlock'
import { userModelList } from '~/utils/business/model'
import type { UserModelListItem } from '~/types/business-api'
import './CreatorStudio.css'

const words = {
  en: {
    title: 'Studio', chat: 'AI chat', image: 'Image', video: 'Video', remix: 'Video remix', project: 'Project creation',
    beta: 'Free', model: 'Model', loadingModels: 'Loading models…', noModels: 'No available models', modelFailed: 'Could not load models.', retry: 'Retry', selectModel: 'Choose a model', credits: 'credits', billed: 'Usage pricing', unavailableModel: 'The saved model is unavailable. Choose another model.', soon: 'Not connected yet', prompt: 'Ask anything…', send: 'Send', stop: 'Stop',
    clear: 'New chat', copy: 'Copy', copied: 'Copied', user: 'You', login: 'Sign in to chat',
    empty: 'What would you like to create?', working: 'Thinking…', failed: 'Could not complete the reply. Try again.',
    interrupted: 'Connection ended before the reply completed.', stopped: 'Stopped.',
    queued: 'Request queued. No reply is available yet. Please do not resend.', expired: 'Your session expired. Sign in again.', limit: 'Conversation limit reached. Start a new chat.',
    restoring: 'Loading your conversation…', storageFailed: 'This browser could not save or restore your chat. Keep this page open to avoid losing it.', storageInvalid: 'Saved chat data is invalid and could not be restored.', copyFailed: 'Could not copy. Select the reply to copy it.', hint: 'Enter to send · Shift + Enter for a new line',
  },
  zh: {
    title: '创作台', chat: 'AI 对话', image: '图片', video: '视频', remix: '视频复刻', project: '项目创作',
    beta: '免费', model: '模型', loadingModels: '正在加载模型…', noModels: '暂无可用模型', modelFailed: '模型加载失败。', retry: '重试', selectModel: '选择模型', credits: '积分', billed: '按用量计费', unavailableModel: '原选模型已不可用，请选择其他模型。', soon: '尚未接入', prompt: '想聊些什么…', send: '发送', stop: '停止接收',
    clear: '新对话', copy: '复制', copied: '已复制', user: '你', login: '登录后对话',
    empty: '今天想创作什么？', working: '思考中…', failed: '回复失败，请重试。',
    interrupted: '连接中断，回复尚未完成。', stopped: '已停止。',
    queued: '请求已排队，暂未收到回复，请勿重复发送。', expired: '登录已失效，请重新登录。', limit: '已达对话上限，请新建对话。',
    restoring: '正在恢复对话…', storageFailed: '浏览器无法保存或恢复对话，请保持页面打开以免丢失。', storageInvalid: '本地对话数据损坏，无法恢复。', copyFailed: '复制失败，请选中回复复制。', hint: 'Enter 发送 · Shift + Enter 换行',
  },
}
type Copy = typeof words.en
type ChatMessage = { role: 'user' | 'assistant'; content: string; modelName?: string }
type Tab = 'chat' | 'image' | 'video' | 'remix' | 'project'

const DEFAULT_CHAT_MODEL = 'creator-elysium-chat'
type SavedChat = { version: 1; messages: ChatMessage[]; draft: string; pending: boolean; modelCode?: string }
const emptyChat = (): SavedChat => ({ version: 1, messages: [], draft: '', pending: false })
function parseSavedChat(raw: string): SavedChat {
  if (raw.length > 800000) throw new Error('invalid')
  const value = JSON.parse(raw)
  if (value?.version !== 1 || !Array.isArray(value.messages) || value.messages.length > 41 ||
    typeof value.draft !== 'string' || value.draft.length > 32000 || typeof value.pending !== 'boolean') throw new Error('invalid')
  let characters = 0
  for (const item of value.messages) {
    if (!item || !['user', 'assistant'].includes(item.role) || typeof item.content !== 'string' || item.content.length > 64000) throw new Error('invalid')
    if (item.modelName != null && (typeof item.modelName !== 'string' || item.modelName.length > 256)) throw new Error('invalid')
    characters += item.content.length
  }
  if (characters > 96000) throw new Error('invalid')
  if (value.modelCode != null && (typeof value.modelCode !== 'string' || value.modelCode.length > 128)) throw new Error('invalid')
  return { version: 1, messages: value.messages.map((item: ChatMessage) => ({ role: item.role, content: item.content, ...(item.modelName ? { modelName: item.modelName } : {}) })), draft: value.draft, pending: value.pending, ...(value.modelCode ? { modelCode: value.modelCode } : {}) }
}

function useModels(kind: 'text' | 'image' | 'video' | 'remix', authenticated: boolean, userId: string) {
  const [models, setModels] = useState<UserModelListItem[]>([])
  const [loading, setLoading] = useState(false)
  const [failed, setFailed] = useState(false)
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    let active = true
    if (!authenticated || !userId) return
    setLoading(true)
    setFailed(false)
    void userModelList({ modelType: kind === 'remix' ? 'video' : kind, ...(kind === 'text' ? { generateMode: 'text' } : {}) })
      .then(items => {
        if (!active) return
        const available = kind === 'remix'
          ? items.filter(item => item.capability?.supportsVideoInput || item.capability?.allowedInputs?.includes('video')) : items
        setModels([...new Map(available.filter(item => item.modelCode).map(item => [item.modelCode, item])).values()])
      }).catch(() => { if (active) { setModels([]); setFailed(true) } })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [kind, authenticated, userId, attempt])
  return { models, loading, failed, retry: () => setAttempt(value => value + 1) }
}

function modelPrice(model: UserModelListItem, text: Copy) {
  return model.isFree ? text.beta : text.billed
}

function ModelSelect({ text, catalog, value, disabled, onChange }: {
  text: Copy; catalog: ReturnType<typeof useModels>; value: string; disabled: boolean; onChange: (value: string) => void
}) {
  const unavailable = Boolean(value) && !catalog.loading && !catalog.failed && !catalog.models.some(item => item.modelCode === value)
  return <div className="creator-model-picker">
    <label><span>{text.model}</span><select aria-label={text.model} value={value} disabled={disabled || catalog.loading || catalog.failed || !catalog.models.length}
      onChange={event => onChange(event.target.value)}>
      {(!value || unavailable || !catalog.models.length) && <option value={value}>{catalog.loading ? text.loadingModels : catalog.models.length ? text.selectModel : text.noModels}</option>}
      {catalog.models.map(item => <option key={item.modelCode} value={item.modelCode}>{item.modelName} · {modelPrice(item, text)}</option>)}
    </select></label>
    {catalog.failed && <p role="alert">{text.modelFailed} <button type="button" onClick={catalog.retry} disabled={disabled}>{text.retry}</button></p>}
    {unavailable && catalog.models.length > 0 && <p role="status">{text.unavailableModel}</p>}
  </div>
}

function MediaPreview({ kind, text, authenticated, userId }: { kind: 'image' | 'video' | 'remix'; text: Copy; authenticated: boolean; userId: string }) {
  const catalog = useModels(kind, authenticated, userId)
  const [modelCode, setModelCode] = useState('')
  const key = userId ? `creator-model:v1:${encodeURIComponent(userId)}:${kind}` : ''
  useEffect(() => {
    if (!key || !authenticated) return
    try { setModelCode(localStorage.getItem(key) || '') } catch { /* Selection still works in memory. */ }
  }, [key, authenticated])
  return <section className="creator-studio-unavailable"><h2>{text[kind]}</h2>
    <ModelSelect text={text} catalog={catalog} value={modelCode} disabled={!authenticated || !userId} onChange={value => {
      setModelCode(value)
      if (key) { try { localStorage.setItem(key, value) } catch { /* Selection still works in memory. */ } }
    }} />
    <p>{text.soon}</p><button type="button" disabled>{text.send}</button></section>
}

function Chat({ text, authenticated, userId }: { text: Copy; authenticated: boolean; userId: string }) {
  const [messages, setMessagesRaw] = useState<ChatMessage[]>([])
  const [draft, setDraftRaw] = useState('')
  const [ready, setReady] = useState(false)
  const [modelCode, setModelCode] = useState(DEFAULT_CHAT_MODEL)
  const catalog = useModels('text', authenticated, userId)
  const selectedModel = catalog.models.find(item => item.modelCode === modelCode)
  const [storageError, setStorageError] = useState('')
  const session = useRef<SavedChat>(emptyChat())
  const hydrated = useRef(false)
  const storageKey = userId ? `creator-chat:v1:${encodeURIComponent(userId)}` : ''
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [copied, setCopied] = useState<number | null>(null)
  const controller = useRef<AbortController | null>(null)
  const end = useRef<HTMLDivElement>(null)
  useEffect(() => {
    let active = true
    // Browser storage is read after hydration, never while Next prerenders the page.
    void Promise.resolve().then(() => {
      if (!active || !authenticated || !storageKey) return
      let restored = emptyChat()
      try {
        const raw = localStorage.getItem(storageKey)
        if (raw !== null) {
          try { restored = parseSavedChat(raw) }
          catch { setStorageError(text.storageInvalid) }
        }
      } catch { setStorageError(text.storageFailed) }
      session.current = restored
      setMessagesRaw(restored.messages)
      setDraftRaw(restored.draft)
      setModelCode(restored.modelCode || DEFAULT_CHAT_MODEL)
      if (restored.pending) setError(text.interrupted)
      hydrated.current = true
      setReady(true)
    })
    return () => { active = false; hydrated.current = false }
  }, [authenticated, storageKey, text.interrupted, text.storageFailed, text.storageInvalid])

  function save(patch: Partial<SavedChat>) {
    if (!hydrated.current || !storageKey || !authenticated) return
    session.current = { ...session.current, ...patch }
    try { localStorage.setItem(storageKey, JSON.stringify(session.current)); setStorageError('') }
    catch { setStorageError(text.storageFailed) }
  }
  function setMessages(next: ChatMessage[]) { save({ messages: next }); setMessagesRaw(next) }
  function setDraft(next: string) { save({ draft: next }); setDraftRaw(next) }

  useEffect(() => () => { controller.current?.abort(); controller.current = null }, [])
  useEffect(() => { end.current?.scrollIntoView({ block: 'nearest' }) }, [messages])

  async function send() {
    if (controller.current || !draft.trim() || !ready || !userId || !selectedModel || catalog.loading || catalog.failed) return
    if (!authenticated) { requireLogin({ redirect: null, leaveAuthRoute: false }); return }
    const history: ChatMessage[] = [...messages.filter(item => item.content.trim()), { role: 'user', content: draft.trim() }]
    if (history.length > 40 || history.reduce((size, item) => size + item.content.length, 0) > 32000) {
      setError(text.limit)
      return
    }
    const responseModelName = selectedModel.modelName
    const assistantMessage = (content: string): ChatMessage => ({ role: 'assistant', content, modelName: responseModelName })
    const request = new AbortController()
    controller.current = request
    save({ pending: true })
    setBusy(true)
    setError('')
    setCopied(null)
    setDraft('')
    setMessages([...history, assistantMessage('')])
    let reader: ReadableStreamDefaultReader<Uint8Array> | undefined
    let answer = ''
    let completed = false
    try {
      const response = await fetch(resolveClientApiUrl('/api/user/creator/chat/stream'), {
        method: 'POST', headers: { ...buildUserApiAuthHeaders(), 'Content-Type': 'application/json', Accept: 'text/event-stream' },
        body: JSON.stringify({ messages: history.map(({ role, content }) => ({ role, content })), modelCode }), signal: request.signal, cache: 'no-store', credentials: 'omit',
      })
      if (response.status === 401) { clearAuthSessionArtifacts(); requireLogin({ redirect: null, leaveAuthRoute: false }); throw new Error(text.expired) }
      if (!response.ok || !response.headers.get('content-type')?.includes('text/event-stream')) {
        const data = await response.json().catch(() => null)
        if (Number(data?.code) === 401) { clearAuthSessionArtifacts(); requireLogin({ redirect: null, leaveAuthRoute: false }); throw new Error(text.expired) }
        throw new Error(typeof data?.message === 'string' ? data.message : typeof data?.msg === 'string' ? data.msg : text.failed)
      }
      reader = response.body?.getReader()
      if (!reader) throw new Error(text.failed)
      const decoder = new TextDecoder()
      let buffer = ''
      const dispatch = (block: string) => {
        const event = parseSseEventBlock(block)
        if (!event || completed) return
        if (!['delta', 'done', 'error'].includes(event.event)) return
        const data: unknown = JSON.parse(event.data || '{}')
        if (!data || typeof data !== 'object') throw new Error(text.failed)
        const value = data as Record<string, unknown>
        if (event.event === 'error') throw new Error(typeof value.message === 'string' ? value.message : text.failed)
        if (event.event === 'done') {
          completed = true
          if (controller.current === request && !request.signal.aborted) {
            if (value.status === 'QUEUED') setError(text.queued)
            else if (typeof value.content === 'string' && value.content) {
              answer = value.content
              setMessages([...history, assistantMessage(answer)])
            }
          }
          return
        }
        if (typeof value.content !== 'string') throw new Error(text.failed)
        answer += value.content
        if (answer.length > 64000) throw new Error(text.failed)
        if (controller.current === request && !request.signal.aborted) setMessages([...history, assistantMessage(answer)])
      }
      while (!completed && !request.signal.aborted) {
        const chunk = await reader.read()
        buffer += chunk.done ? decoder.decode() : decoder.decode(chunk.value, { stream: true })
        if (buffer.length > 262144) throw new Error(text.failed)
        // Keep CRLF intact across network chunks; the parser accepts CRLF lines.
        let separator = /\r?\n\r?\n/.exec(buffer)
        while (separator && !completed) {
          dispatch(buffer.slice(0, separator.index))
          buffer = buffer.slice(separator.index + separator[0].length)
          separator = /\r?\n\r?\n/.exec(buffer)
        }
        if (chunk.done) { if (buffer.trim() && !completed) dispatch(buffer); break }
      }
      if (!completed && !request.signal.aborted) throw new Error(text.interrupted)
    } catch (cause) {
      if (controller.current === request && !request.signal.aborted) setError(cause instanceof Error ? cause.message : text.failed)
    } finally {
      await reader?.cancel().catch(() => undefined)
      if (controller.current === request) { controller.current = null; save({ pending: false }); setBusy(false) }
    }
  }

  function stop() {
    controller.current?.abort()
    controller.current = null
    setBusy(false)
    save({ pending: false })
    setError(text.stopped)
  }
  function clear() {
    controller.current?.abort()
    controller.current = null
    setBusy(false)
    session.current = { ...emptyChat(), modelCode }
    setMessagesRaw([])
    setDraftRaw('')
    setError('')
    setCopied(null)
    if (hydrated.current && storageKey && authenticated) {
      try {
        if (modelCode === DEFAULT_CHAT_MODEL) localStorage.removeItem(storageKey)
        else localStorage.setItem(storageKey, JSON.stringify(session.current))
        setStorageError('')
      }
      catch { setStorageError(text.storageFailed) }
    }
  }
  async function copyReply(content: string, index: number) {
    try { await navigator.clipboard.writeText(content); setCopied(index) }
    catch { setError(text.copyFailed) }
  }

  return <section className="creator-chat" aria-label={text.chat}>
    <header className="creator-chat-header"><ModelSelect text={text} catalog={catalog} value={modelCode} disabled={busy || !ready}
      onChange={value => { setModelCode(value); save({ modelCode: value }); setError('') }} />
      <button type="button" disabled={!ready} onClick={clear}>{text.clear}</button></header>
    <div className="creator-chat-messages" aria-busy={busy}>
      {!messages.length && <h2 className="creator-chat-empty">{text.empty}</h2>}
      {messages.map((item, index) => !item.content && !busy ? null : <article key={index} className={`creator-chat-message is-${item.role}`}>
        <strong>{item.role === 'user' ? text.user : item.modelName || 'Elysium Chat'}</strong>
        <p>{item.content || (busy && index === messages.length - 1 ? text.working : '—')}</p>
        {item.role === 'assistant' && item.content && <button type="button" onClick={() => void copyReply(item.content, index)}>
          {copied === index ? text.copied : text.copy}</button>}
      </article>)}
      <div ref={end} />
    </div>
    <form className="creator-chat-composer" onSubmit={event => { event.preventDefault(); void send() }}>
      {authenticated && !ready && <p role="status">{text.restoring}</p>}
      {storageError && <p className="creator-chat-error" role="alert">{storageError}</p>}
      {error && <p className="creator-chat-error" role="alert">{error}</p>}
      <textarea aria-label={text.prompt} placeholder={text.prompt} value={draft} maxLength={32000} rows={3}
        disabled={busy || !ready} onChange={event => setDraft(event.target.value)} onKeyDown={event => {
          if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) { event.preventDefault(); void send() }
        }} />
      <div className="creator-chat-actions"><small>{text.hint}</small>
        {busy ? <button type="button" className="creator-chat-primary" onClick={stop}>{text.stop}</button>
          : !authenticated ? <button type="button" className="creator-chat-primary" onClick={() => requireLogin({ redirect: null, leaveAuthRoute: false })}>{text.login}</button>
          : <button type="submit" className="creator-chat-primary" disabled={!ready || !draft.trim() || !selectedModel || catalog.loading || catalog.failed}>{text.send} ↑</button>}
      </div>
    </form>
  </section>
}

export default function CreatorStudio() {
  const { locale } = useCreatorLocale()
  const text = words[locale === 'zh' || locale === 'zh-hant' ? 'zh' : 'en']
  const token = useUserStore(state => state.token)
  const userId = useUserStore(state => state.user?.id)
  const [tab, setTab] = useState<Tab>('chat')
  return <main className="creator-studio">
    <h1>{text.title}</h1>
    <div className="creator-studio-tabs" role="tablist" aria-label={text.title}>
      {(['chat', 'image', 'video', 'remix', 'project'] as const).map(value => <button key={value} type="button"
        role="tab" aria-selected={tab === value} aria-controls={`creator-studio-${value}`} id={`creator-tab-${value}`}
        onClick={() => setTab(value)}>{text[value]}</button>)}
    </div>
    <div role="tabpanel" id={`creator-studio-${tab}`} aria-labelledby={`creator-tab-${tab}`}>
      {tab === 'chat' ? <Chat key={token && userId ? String(userId) : 'anonymous'} userId={token && userId ? String(userId) : ''} text={text} authenticated={Boolean(token)} />
        : tab === 'project' ? <CreatorDashboard />
        : <MediaPreview key={`${userId || 'anonymous'}:${tab}`} kind={tab} text={text} authenticated={Boolean(token)} userId={token && userId ? String(userId) : ''} />}
    </div>
  </main>
}
