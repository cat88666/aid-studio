'use client'

/* eslint-disable @next/next/no-img-element -- 展示静态导出的首页剧照 */

import { useCreatorLocale } from '~/hooks/useCreatorLocale'
import { withAppBasePath } from '~/utils/appBasePath'
import { HtmlShellClass } from '@/components/app/HtmlShellClass'
import { creatorBrandName } from '~/config/creatorBrand'
import { message } from 'antd'
import { useSyncExternalStore } from 'react'
import styles from './mobile-page.module.css'

const posterUrl = withAppBasePath('/creator-home/hero.webp')
const subscribeToLocation = () => () => undefined
const getBrowserHost = () => window.location.host + withAppBasePath('/').replace(/\/$/, '')
const getServerHost = () => ''
const getBrowserOrigin = () => window.location.origin + withAppBasePath('/')
const getServerOrigin = () => ''

/** 移动端创作入口提供可复制的电脑端地址。 */
export default function MobilePage() {
  const { locale } = useCreatorLocale()
  const english = locale === 'en'
  const siteAddress = useSyncExternalStore(subscribeToLocation, getBrowserHost, getServerHost)
  const siteOrigin = useSyncExternalStore(subscribeToLocation, getBrowserOrigin, getServerOrigin)

  async function copyPcUrl() {
    const text = siteOrigin || (typeof window !== 'undefined' ? window.location.origin : '')
    if (!text) {
      message.error(english ? 'Copy the address manually.' : '复制失败，请手动复制地址')
      return
    }
    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(text)
      } else {
        const input = document.createElement('input')
        input.value = text
        document.body.appendChild(input)
        input.select()
        document.execCommand('copy')
        document.body.removeChild(input)
      }
      message.success(english ? 'Address copied.' : '电脑端地址已复制')
    } catch {
      message.error(english ? 'Copy the address manually.' : '复制失败，请手动复制地址')
    }
  }

  return (
    <div className={styles.page}>
      <HtmlShellClass classes="mobile-only-shell" />
      <div className={styles.panel}>
        <span className={styles.logo}>{creatorBrandName(locale)}</span>
        <div className={styles.hero}>
          <img src={posterUrl} alt="" className={styles.heroImage} />
        </div>
        <div className={styles.tip}>
          <p className={styles.tipTitle}>{english ? 'Open the studio on a computer' : '请使用电脑端打开本网页'}</p>
          <p className={styles.tipDomain}>{siteAddress}</p>
          <button type="button" className={styles.copyButton} onClick={copyPcUrl}>
            {english ? 'Copy desktop link' : '复制电脑端地址'}
          </button>
        </div>
      </div>
    </div>
  )
}
