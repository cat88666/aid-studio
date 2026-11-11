'use client'

import Link from 'next/link'
import { useState } from 'react'
import { userGuideSections } from '~/content/guide-user'
import { developerGuideSections } from '~/content/guide-developer'
import './OfficialGuide.css'

type GuideMode = 'overview' | 'user' | 'developer'
const paths = [
  { mode: 'overview', href: '/guide/', label: '指南首页' },
  { mode: 'user', href: '/guide/user/', label: '使用教程' },
  { mode: 'developer', href: '/guide/developer/', label: '开发文档' }
] as const

export default function OfficialGuide({ mode = 'overview' }: { mode?: GuideMode }) {
  const [query, setQuery] = useState('')
  const sections = mode === 'developer' ? developerGuideSections : userGuideSections
  const keyword = query.trim().toLocaleLowerCase()
  const visible = sections.filter(section => [section.title, section.summary, ...section.paragraphs, ...(section.steps || []), ...(section.tips || [])].join(' ').toLocaleLowerCase().includes(keyword))
  const title = mode === 'overview' ? '官方指导' : mode === 'user' ? '使用教程' : '开发文档'

  return (
    <main className="official-guide" lang="zh-CN">
      <header className="official-guide__hero">
        <div>
          <p className="official-guide__eyebrow">创作台 · 帮助中心</p>
          <h1>{title}</h1>
          <p>{mode === 'overview' ? '从第一份剧本到项目制作，找到每一步的操作方法。' : mode === 'user' ? '按流程开始创作，也可以直接搜索遇到的问题。' : '了解项目结构、开发流程与接口边界，在自己的环境中验证和维护。'}</p>
        </div>
        <Link className="official-guide__action" href="/start/">进入创作台 <span aria-hidden="true">↗</span></Link>
      </header>
      <nav className="official-guide__tabs" aria-label="指导分类">
        {paths.map(item => <Link key={item.mode} href={item.href} aria-current={mode === item.mode ? 'page' : undefined}>{item.label}</Link>)}
      </nav>

      {mode === 'overview' ? (
        <>
          <section className="official-guide__choices" aria-label="选择阅读指南">
            <Link href="/guide/user/" className="official-guide__choice">
              <span className="official-guide__number">01 · 创作者</span><h2>使用教程</h2>
              <p>了解账号、项目、模型选择与制作流程。遇到额度、任务或素材问题时，按提示逐项检查。</p>
              <span className="official-guide__tags">项目与剧本 · 素材与分镜 · 模型与费用</span>
              <strong>开始阅读 <span aria-hidden="true">→</span></strong>
            </Link>
            <Link href="/guide/developer/" className="official-guide__choice">
              <span className="official-guide__number">02 · 开发者</span><h2>开发文档</h2>
              <p>从源码结构和开发环境开始，理解前后端接口、模型网关、任务状态与发布检查。</p>
              <span className="official-guide__tags">环境与构建 · 接口与任务 · 发布与排障</span>
              <strong>查看文档 <span aria-hidden="true">→</span></strong>
            </Link>
          </section>
          <section className="official-guide__intro" aria-labelledby="guide-first-project">
            <h2 id="guide-first-project">第一次使用，从这里开始</h2>
            <div className="official-guide__quick-links">
              <Link href="/guide/user/">1. 了解创作流程<span>从账号、素材和项目准备开始</span></Link>
              <Link href="/start/">2. 打开创作台<span>先用 AI 对话整理故事与需求</span></Link>
              <Link href="/projects/">3. 查看项目与示例<span>继续已有作品，或浏览参考样片</span></Link>
            </div>
          </section>
          <aside className="official-guide__note"><strong>使用前请留意</strong><p>模型是否可用、生成费用和流程开放状态，以当前页面提示为准。免费模型存在共享额度和上游限流；独立图片、视频、视频复刻入口显示“尚未接入”时，暂不能提交生成。</p></aside>
          <p className="official-guide__edition">本站原创指南 · 按当前项目功能整理 · 2026-11-11 更新</p>
        </>
      ) : (
        <>
          <div className="official-guide__search">
            <label htmlFor="guide-search">搜索本篇指南</label>
            <div><input id="guide-search" type="search" value={query} onChange={event => setQuery(event.target.value)} placeholder="例如：模型、剧本、费用、构建" />{query && <button type="button" onClick={() => setQuery('')}>清空</button>}</div>
            <span role="status">{keyword ? `找到 ${visible.length} 个章节` : `共 ${sections.length} 个章节`}</span>
          </div>
          <div className="official-guide__layout">
            <aside className="official-guide__toc"><details open><summary>本篇目录</summary><nav aria-label="本篇目录">{visible.map(section => <a key={section.id} href={`#${section.id}`}>{section.title}</a>)}</nav></details></aside>
            <div className="official-guide__articles">
              {visible.length === 0 && <div className="official-guide__empty"><h2>没有找到相关章节</h2><p>试试更短的关键词，或清空搜索查看全部内容。</p><button onClick={() => setQuery('')}>查看全部章节</button></div>}
              {visible.map(section => (
                <article id={section.id} key={section.id} className="official-guide__article">
                  <h2>{section.title}</h2><p className="official-guide__summary">{section.summary}</p>
                  {section.paragraphs.map((paragraph, index) => <p key={index}>{paragraph}</p>)}
                  {section.steps && <ol>{section.steps.map((step, index) => <li key={index}>{step}</li>)}</ol>}
                  {section.tips && <aside className="official-guide__tips"><strong>提示</strong><ul>{section.tips.map((tip, index) => <li key={index}>{tip}</li>)}</ul></aside>}
                  {section.links && <div className="official-guide__related">{section.links.map(link => <Link key={link.href} href={link.href}>{link.label} →</Link>)}</div>}
                </article>
              ))}
              <p className="official-guide__edition">本站原创指南 · 2026-11-11 更新 · 操作和可用性以当前界面为准</p>
            </div>
          </div>
        </>
      )}
    </main>
  )
}
