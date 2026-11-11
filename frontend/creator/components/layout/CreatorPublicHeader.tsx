'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import navLabels from '~/config/dashboardNav.json'
import { useEffect, useRef, useState } from 'react'
import { GlobalOutlined, MenuOutlined } from '@ant-design/icons'
import { creatorBrandName, CREATOR_BRAND_ICON } from '~/config/creatorBrand'
import { creatorLanguage, creatorLanguages } from '~/config/creatorLanguages'
import { homeCopy } from '~/config/home-locales'
import { useCreatorLocale } from '~/hooks/useCreatorLocale'
import { useUserStore } from '~/stores/user'
import { withAppBasePath } from '~/utils/appBasePath'
import { requireLogin } from '~/utils/authLoginNavigation'
import '~/app/(home)/creator-home.css'

export default function CreatorPublicHeader({ onAccount }: { onAccount: () => void }) {
  const { locale, setLocale } = useCreatorLocale()
  const language = creatorLanguage(locale)
  const t = homeCopy[locale]
  const languageMenu = useRef<HTMLDetailsElement>(null)
  const [menuOpen, setMenuOpen] = useState(false)
  const token = useUserStore(state => state.token)
  const pathname = usePathname()?.replace(/\/$/, '') || '/'
  const home = language.path
  const studioLabel = locale === 'zh' ? '创作台' : locale === 'zh-hant' ? '創作台' : 'Studio'
  const destinations = [home, '/start/', '/projects/', '/assets/', '/billing/', '/referral/']
  const labels = [navLabels[locale][0], studioLabel, navLabels[locale][1], navLabels[locale][2], navLabels[locale][4], navLabels[locale][5]]
  const current = pathname === '/projects' ? 2 : pathname === '/start' ? 1 : pathname === '/assets' ? 3 : pathname === '/billing' || pathname === '/membership' ? 4 : pathname === '/referral' || pathname === '/invite' ? 5 : pathname === '/' || pathname === home.replace(/\/$/, '') ? 0 : -1

  useEffect(() => {
    const dismissOutside = (event: PointerEvent) => {
      const menu = languageMenu.current
      if (menu?.open && event.target instanceof Node && !menu.contains(event.target)) menu.open = false
    }
    const handleEscape = (event: KeyboardEvent) => {
      const menu = languageMenu.current
      if (event.key === 'Escape' && menu?.open) {
        menu.open = false
        menu.querySelector('summary')?.focus()
      }
    }
    document.addEventListener('pointerdown', dismissOutside)
    document.addEventListener('keydown', handleEscape)
    return () => {
      document.removeEventListener('pointerdown', dismissOutside)
      document.removeEventListener('keydown', handleEscape)
    }
  }, [])


  return (
    <>
      <header className="creator-nav dashboard-nav">
      <div className="creator-container">
        <Link href={language.path} aria-label={creatorBrandName(locale) + ' ' + t.home} className="creator-brand">
          <img src={CREATOR_BRAND_ICON} width="26" height="26" alt="" />{creatorBrandName(locale)}
        </Link>
        <nav className={menuOpen ? 'creator-links is-open' : 'creator-links'} aria-label={t.nav}>
          {destinations.map((href, index) => (
            <Link key={href} href={href} prefetch={false} aria-current={index === current ? 'page' : undefined}
              onClick={() => setMenuOpen(false)}>{labels[index]}</Link>
          ))}
        </nav>
        <details className="creator-language" ref={languageMenu}>
          <summary aria-label={t.language + ' · ' + language.label}>
            <GlobalOutlined aria-hidden="true" /><span lang={language.tag}>{language.label}</span>
          </summary>
          <ul aria-label={t.language}>
            {creatorLanguages.map(entry => (
              <li key={entry.code}>
                <a href={withAppBasePath(entry.path)} lang={entry.tag} hrefLang={entry.tag}
                  aria-current={entry.code === locale ? 'page' : undefined}
                  onClick={event => {
                    if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return
                    event.preventDefault()
                    if (languageMenu.current) languageMenu.current.open = false
                    setLocale(entry.code)
                  }}>
                  {entry.label}
                </a>
              </li>
            ))}
          </ul>
        </details>
        <button type="button" className="creator-nav-account" onClick={() => token ? onAccount() : requireLogin({ redirect: null, leaveAuthRoute: false })}>{token ? t.account : t.login}</button>
        <button type="button" className="creator-menu" aria-label={t.menu} aria-expanded={menuOpen} onClick={() => setMenuOpen(!menuOpen)}><MenuOutlined /></button>
      </div>
    </header>
    </>
  )
}
