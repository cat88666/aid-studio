'use client'

import { SAKURA_ANTD_THEME } from '@/config/antdTheme'
import { App, ConfigProvider } from 'antd'
import enUS from 'antd/locale/en_US'
import zhCN from 'antd/locale/zh_CN'
import { useCreatorLocale } from '~/hooks/useCreatorLocale'
import { type ReactNode } from 'react'
import { getSakuraPortalRoot } from '~/utils/portalRoot'

export const SAKURA_ANTD_STATIC_HOLDER_CLASS = 'sakura-antd-static-holder'

/** 锚定浮层复用 Portal；无锚点的全局消息必须脱离业务 Portal 的 stacking context。 */
export function resolveSakuraPopupContainer(triggerNode?: HTMLElement): HTMLElement {
  if (!triggerNode) return document.body
  return getSakuraPortalRoot() ?? triggerNode?.parentElement ?? document.body
}

/** Static Modal/message/notification APIs render outside the React provider tree. */
export function renderSakuraAntdStaticHolder(children: ReactNode) {
  return (
    <ConfigProvider
      locale={enUS}
      theme={SAKURA_ANTD_THEME}
      wave={{ disabled: true }}
      getPopupContainer={resolveSakuraPopupContainer}
    >
      <App component={false}>
        <div className={SAKURA_ANTD_STATIC_HOLDER_CLASS}>{children}</div>
      </App>
    </ConfigProvider>
  )
}

let staticThemeInstalled = false

/** Idempotent for React Strict Mode and Fast Refresh. */
export function installSakuraAntdStaticTheme(): void {
  if (staticThemeInstalled) return
  staticThemeInstalled = true
  ConfigProvider.config({
    theme: SAKURA_ANTD_THEME,
    holderRender: renderSakuraAntdStaticHolder
  })
}

export function AntdThemeProvider({ children }: { children: ReactNode }) {
  const { locale } = useCreatorLocale()
  return (
    <ConfigProvider
      locale={locale === 'zh' || locale === 'zh-hant' ? zhCN : enUS}
      theme={SAKURA_ANTD_THEME}
      wave={{ disabled: true }}
      getPopupContainer={resolveSakuraPopupContainer}
    >
      <App component={false}>{children}</App>
    </ConfigProvider>
  )
}
