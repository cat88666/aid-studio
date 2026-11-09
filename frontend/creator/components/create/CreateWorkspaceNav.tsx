'use client'

import { useEffect } from 'react'
import PublicBrandLogo from '~/components/atoms/PublicBrandLogo'
import { useUserStore } from '~/stores/user'
import { useAuthPublicConfig } from '~/composables/useAuthPublicConfig'
import { formatCreditAmount } from '~/components/common/recharge/rechargeFormat'
import { useCreateCopy } from './useCreateCopy'

interface Props {
  onBrand: () => void
  onWorks: () => void
  onAssets: () => void
  onTutorial: () => void
  onInvite: () => void
  onBilling: () => void
  onLogin: () => void
  onToggleUserMenu: () => void
  onUserMenuTriggerChange: (element: HTMLElement | null) => void
}

export default function CreateWorkspaceNav(props: Props) {
  const t = useCreateCopy()
  const token = useUserStore(s => s.token)
  const user = useUserStore(s => s.user)
  const { invitePromotionEnabled, loadPublicConfig } = useAuthPublicConfig()
  useEffect(() => { void loadPublicConfig() }, [loadPublicConfig])
  return (
    <header className="create-workspace-nav">
      <button type="button" className="create-workspace-nav__brand" onClick={props.onBrand} aria-label={t('首页', 'Home')}><PublicBrandLogo /></button>
      <nav aria-label={t('主要导航', 'Main navigation')}>
        <button type="button" onClick={props.onWorks}>{t('项目', 'Projects')}</button>
        <button type="button" onClick={props.onAssets}>{t('资产库', 'Assets')}</button>
        <button type="button" onClick={props.onTutorial}>{t('教程', 'Help')}</button>
        {invitePromotionEnabled && <button type="button" onClick={props.onInvite}>{t('邀请好友', 'Invite')}</button>}
      </nav>
      <div className="create-workspace-nav__account">
        {token ? <>
          <button type="button" onClick={props.onBilling}>{formatCreditAmount(Number(user?.balance ?? 0))} <span>{t('积分', 'credits')}</span></button>
          <button type="button" ref={props.onUserMenuTriggerChange} onClick={props.onToggleUserMenu} aria-label={t('账户菜单', 'Account menu')}>{t('账户', 'Account')}</button>
        </> : <button type="button" onClick={props.onLogin}>{t('登录', 'Log in')}</button>}
      </div>
    </header>
  )
}
