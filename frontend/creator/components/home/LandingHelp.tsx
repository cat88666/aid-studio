'use client'

import { useEffect, useId, useState } from 'react'
import type { CreatorLocale } from '~/config/creatorLanguages'
import type { UserFaqListItem } from '~/types/business-api'
import { userFaqDetail, userFaqList } from '~/utils/business/misc'
import { sanitizeDisplayHtml } from '~/utils/safeDisplayHtml'
import { faqLabels, type HelpLabels } from './faqLabels'
import './LandingHelp.css'

function LoadError({ text, retry }: { text: HelpLabels; retry: () => void }) {
  const [, , , , failureLabel, retryLabel] = text
  return <p role="alert">{failureLabel} <button className="landing-help-retry" type="button" onClick={retry}>{retryLabel}</button></p>
}

function Question({ item, text }: { item: UserFaqListItem; text: HelpLabels }) {
  const [opened, setOpened] = useState(false)
  const [answer, setAnswer] = useState<string | null>(null)
  const [failed, setFailed] = useState(false)
  const [attempt, setAttempt] = useState(0)
  const [, , loadingLabel, , , , noAnswerLabel] = text

  useEffect(() => {
    if (!opened) return
    let current = true
    userFaqDetail({ id: item.id }).then(detail => {
      if (current) setAnswer(sanitizeDisplayHtml(detail.content))
    }).catch(() => { if (current) setFailed(true) })
    return () => { current = false }
  }, [item.id, opened, attempt])

  return <details onToggle={event => { if (event.currentTarget.open) setOpened(true) }}>
    <summary dir="auto">{item.title}</summary>
    {failed ? <LoadError text={text} retry={() => { setFailed(false); setAttempt(n => n + 1) }} />
      : answer ? <div className="landing-help-answer" dir="auto" dangerouslySetInnerHTML={{ __html: answer }} />
      : <p role="status">{answer === null ? loadingLabel : noAnswerLabel}</p>}
  </details>
}

export function LandingHelp({ locale }: { locale: CreatorLocale }) {
  const text = faqLabels[locale]
  const [searchLabel, allLabel, loadingLabel, emptyLabel] = text
  const searchId = useId()
  const [search, setSearch] = useState('')
  const [category, setCategory] = useState('')
  const [items, setItems] = useState<UserFaqListItem[]>([])
  const [status, setStatus] = useState<'loading' | 'ready' | 'failed'>('loading')
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let current = true
    const keyword = search.trim()
    async function load() {
      const results: UserFaqListItem[] = []
      try {
        for (let pageNum = 1; pageNum <= 100; pageNum++) {
          const page = await userFaqList({ pageNum, pageSize: 50, keyword: keyword || undefined })
          if (!current) return
          results.push(...page.rows)
          if (!page.hasMore || !page.rows.length) break
        }
        setItems(results)
        setStatus('ready')
      } catch {
        if (current) setStatus('failed')
      }
    }
    const timer = setTimeout(() => { void load() }, keyword ? 250 : 0)
    return () => { current = false; clearTimeout(timer) }
  }, [search, attempt])

  const categories = Array.from(new Set(items.map(item => item.category?.trim()).filter((value): value is string => !!value)))
  const visible = category ? items.filter(item => item.category?.trim() === category) : items
  return <div className="landing-help">
    <label htmlFor={searchId}>{searchLabel}</label>
    <input id={searchId} type="search" value={search} onChange={event => { setSearch(event.target.value); setCategory(''); setStatus('loading') }} />
    {status === 'loading' ? <p role="status">{loadingLabel}</p>
      : status === 'failed' ? <LoadError text={text} retry={() => { setStatus('loading'); setAttempt(n => n + 1) }} />
      : <>
        {categories.length > 0 && <div className="landing-help-categories">
          {['', ...categories].map(name => <button type="button" key={name} aria-pressed={name === category} onClick={() => setCategory(name)}>
            {name || allLabel}
          </button>)}
        </div>}
        {visible.length ? visible.map(item => <Question key={item.id} item={item} text={text} />) : <p role="status">{emptyLabel}</p>}
      </>}
  </div>
}
