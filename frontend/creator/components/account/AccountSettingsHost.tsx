'use client'

import {
  ContactsOutlined,
  DeleteOutlined,
  HistoryOutlined,
  LaptopOutlined,
  LockOutlined,
  WechatOutlined
} from '@ant-design/icons'
import { Button,Drawer,Spin } from 'antd'
import { usePathname,useRouter } from 'next/navigation'
import { useCallback,useEffect,useState } from 'react'
import { closeAccountSettings,useAccountSettingsStore,type AccountSettingsSection } from '~/stores/accountSettings'
import { useUserStore } from '~/stores/user'
import type { AccountSecurityData } from '~/types/business-api'
import { accountApiErrorMessage } from '~/utils/accountSecurity'
import { logoutToPublicHome } from '~/utils/authLoginNavigation'
import { accountSecurity } from '~/utils/businessApi'
import {
  AccountCancelSection,
  AccountContactSection,
  AccountLoginHistorySection,
  AccountPasswordSection,
  AccountSessionsSection,
  AccountWechatSection
} from './AccountSettingsSections'
import { useAccountVerificationCode } from './useAccountVerificationCode'
import './account-settings.css'

const NAV_ITEMS: Array<{
  key: AccountSettingsSection
  label: string
  icon: typeof LockOutlined
  danger?: boolean
}> = [
  { key: 'password', label: '修改密码', icon: LockOutlined },
  { key: 'contact', label: '手机号或邮箱', icon: ContactsOutlined },
  { key: 'wechat', label: '微信绑定', icon: WechatOutlined },
  { key: 'sessions', label: '设备登录管理', icon: LaptopOutlined },
  { key: 'history', label: '登录历史', icon: HistoryOutlined },
  { key: 'cancel', label: '注销账号', icon: DeleteOutlined, danger: true }
]

export function AccountSettingsHost() {
  const open = useAccountSettingsStore((state) => state.open)
  const section = useAccountSettingsStore((state) => state.section)
  const setSection = useAccountSettingsStore((state) => state.setSection)
  const token = useUserStore((state) => state.token)
  const pathname = usePathname()
  const router = useRouter()
  const codes = useAccountVerificationCode()
  const [security, setSecurity] = useState<AccountSecurityData | null>(null)
  const [loading, setLoading] = useState(false)
  const [loadError, setLoadError] = useState('')

  const refreshSecurity = useCallback(async () => {
    setLoading(true)
    setLoadError('')
    try {
      setSecurity(await accountSecurity())
      void useUserStore.getState().fetchProfile()
    } catch (error) {
      setLoadError(accountApiErrorMessage(error, '账号安全信息加载失败'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (!open) return
    if (!token) {
      closeAccountSettings()
      return
    }
    void refreshSecurity()
  }, [open, refreshSecurity, token])

  useEffect(() => {
    if (open) closeAccountSettings()
    // 切换页面时关闭账号设置，避免跨页面残留遮罩。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pathname])

  const handleAuthInvalidated = useCallback(() => {
    closeAccountSettings()
    logoutToPublicHome((href) => router.replace(href))
  }, [router])

  const sectionProps = security
    ? { security, codes, refreshSecurity, onAuthInvalidated: handleAuthInvalidated }
    : null

  return (
    <Drawer
      open={open}
      onClose={closeAccountSettings}
      title="账号设置"
      size="min(920px, calc(100vw - 24px))"
      rootClassName="account-settings-drawer"
      destroyOnHidden
      mask={{ closable: true }}
      styles={{ mask: { pointerEvents: open ? 'auto' : 'none' } }}
    >
      <div id="account-settings-captcha-box" className="account-settings__captcha" aria-hidden="true" />
      <div className="account-settings__layout">
        <nav className="account-settings__nav" aria-label="账号设置功能">
          {NAV_ITEMS.map((item) => {
            const Icon = item.icon
            return (
              <button
                key={item.key}
                type="button"
                className={`${section === item.key ? 'is-active' : ''}${item.danger ? ' is-danger' : ''}`}
                onClick={() => setSection(item.key)}
              >
                <span className="account-settings__nav-icon">
                  <Icon />
                </span>
                <span>{item.label}</span>
              </button>
            )
          })}
        </nav>
        <main className="account-settings__content">
          {loading && !security ? (
            <div className="account-settings__loading"><Spin /><span>正在加载账号信息…</span></div>
          ) : loadError && !security ? (
            <div className="account-settings__load-error"><span>{loadError}</span><Button onClick={() => void refreshSecurity()}>重新加载</Button></div>
          ) : sectionProps ? (
            <>
              {section === 'password' && <AccountPasswordSection {...sectionProps} />}
              {section === 'contact' && <AccountContactSection {...sectionProps} />}
              {section === 'wechat' && <AccountWechatSection {...sectionProps} />}
              {section === 'sessions' && <AccountSessionsSection {...sectionProps} />}
              {section === 'history' && <AccountLoginHistorySection />}
              {section === 'cancel' && <AccountCancelSection {...sectionProps} />}
            </>
          ) : null}
        </main>
      </div>
    </Drawer>
  )
}
