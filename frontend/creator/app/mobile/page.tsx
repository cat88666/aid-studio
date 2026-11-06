'use client'

/* eslint-disable @next/next/no-img-element -- 展示静态导出的首页剧照 */

import { withAppBasePath } from '~/utils/appBasePath'
import { HtmlShellClass } from '@/components/app/HtmlShellClass'
import { CREATOR_BRAND_NAME } from '~/config/creatorBrand'
import { message } from 'antd'
import { useSyncExternalStore } from 'react'
import styles from './mobile-page.module.css'

const posterUrl = withAppBasePath('/creator-home/hero.webp')
const subscribeToLocation = () => () => undefined
const getBrowserHost = () => window.location.host + withAppBasePath('/').replace(/\/$/, '')
const getServerHost = () => '当前站点'
const getBrowserOrigin = () => window.location.origin + withAppBasePath('/')
const getServerOrigin = () => ''

/** 移动端创作入口提供可复制的电脑端地址。 */
export default function MobilePage() {
  const siteAddress = useSyncExternalStore(subscribeToLocation, getBrowserHost, getServerHost)
  const siteOrigin = useSyncExternalStore(subscribeToLocation, getBrowserOrigin, getServerOrigin)

  async function copyPcUrl() {
    const text = siteOrigin || (typeof window !== 'undefined' ? window.location.origin : '')
    if (!text) {
      message.error('复制失败，请手动复制地址')
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
      message.success('电脑端地址已复制')
    } catch {
      message.error('复制失败，请手动复制地址')
    }
  }

  return (
    <div className={styles.page}>
      <HtmlShellClass classes="mobile-only-shell" />
      <div className={styles.panel}>
        <span className={styles.logo}>{CREATOR_BRAND_NAME}</span>
        <div className={styles.hero}>
          <img src={posterUrl} alt="" className={styles.heroImage} />
        </div>
        <div className={styles.tip}>
          <p className={styles.tipTitle}>请使用电脑端打开本网页</p>
          <p className={styles.tipDomain}>{siteAddress}</p>
          <button type="button" className={styles.copyButton} onClick={copyPcUrl}>
            复制电脑端地址
          </button>
        </div>
      </div>
    </div>
  )
}
