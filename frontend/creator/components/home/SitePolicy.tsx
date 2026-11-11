import Link from 'next/link'
import { sitePolicies, type SitePolicyId } from '~/content/site-policies'
import './OfficialGuide.css'
import './SitePolicy.css'

export default function SitePolicy({ id }: { id: SitePolicyId }) {
  const policy = sitePolicies[id]
  return (
    <main className="official-guide site-policy" lang="zh-CN">
      <header className="official-guide__hero">
        <div><p className="official-guide__eyebrow">创作台 · 服务说明</p><h1>{policy.title}</h1><p>{policy.description}</p></div>
        <Link className="official-guide__action" href="/start/">返回创作台 →</Link>
      </header>
      <nav className="official-guide__tabs" aria-label="服务说明分类">
        {(Object.keys(sitePolicies) as SitePolicyId[]).map(key => <Link key={key} href={`/policies/${key}/`} aria-current={key === id ? 'page' : undefined}>{sitePolicies[key].title}</Link>)}
      </nav>
      {policy.sections.map((section, index) => (
        <article id={section.id} className="official-guide__article" key={section.title}>
          <h2>{index + 1}. {section.title}</h2>
          {section.paragraphs.map(paragraph => <p key={paragraph}>{paragraph}</p>)}
        </article>
      ))}
      <p className="official-guide__edition">本站原创说明 · 2026-11-11 更新</p>
    </main>
  )
}
