'use client'

import { useAuthText } from '~/hooks/useAuthText'

import { useEffect, useRef, useState } from 'react'
import { LoginAgreementHint } from './LoginAgreementHint'
import { ConfigProvider, message } from 'antd'
import { useRouter } from 'next/navigation'
import { useCreatorLocale } from '~/hooks/useCreatorLocale'
import { creatorBrandName } from '~/config/creatorBrand'
import { LoginCodePanel } from './LoginCodePanel'
import { LoginPasswordPanel } from './LoginPasswordPanel'
import { LoginWechatPanel } from './LoginWechatPanel'
import { useLoginModalSession } from '@/hooks/useLoginModalSession'
import { closeLoginModal, enterForgotPasswordFlow } from '@/stores/loginModal'
import { LOGIN_MODAL_ANTD_THEME } from '~/utils/loginModalAntdTheme'
import {
  LOGIN_MODAL_BODY_CLASS,
  LOGIN_MODAL_BRAND_CLASS,
  LOGIN_MODAL_CLOSE_CLASS,
  LOGIN_MODAL_FIT_SHELL_CLASS,
  LOGIN_MODAL_FORM_CLASS,
  LOGIN_MODAL_FRAME_CLASS,
  LOGIN_MODAL_OVERLAY_CLASS,
  LOGIN_MODAL_TITLE_CLASS,
} from '~/utils/loginModalLayout'

const TITLE_MAP = {
  code: '验证码登录',
  wechat: '微信扫码登录',
  password: '密码登录'
} as const

interface LoginModalProps {
  /** Host 控制的进场可见态（退场时先 false 再卸载） */
  visible?: boolean
}

export function LoginModal({ visible = true }: LoginModalProps) {
  const { locale } = useCreatorLocale()
  const t = useAuthText()
  const router = useRouter()
  const session = useLoginModalSession()
  const [choosing, setChoosing] = useState(() => session.tab === 'code')
  const dialogRef = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const opener = document.activeElement instanceof HTMLElement ? document.activeElement : null
    return () => { if (opener?.isConnected) opener.focus() }
  }, [])
  useEffect(() => {
    if (visible) dialogRef.current?.focus()
  }, [visible, choosing])
  function trapFocus(event: React.KeyboardEvent<HTMLDivElement>) {
    if (event.key !== 'Tab') return
    const nodes = Array.from(event.currentTarget.querySelectorAll<HTMLElement>(
      'button:not(:disabled), a[href], input:not(:disabled), [tabindex="0"]'
    )).filter((node) => node.getClientRects().length > 0)
    const first = nodes[0]
    const last = nodes[nodes.length - 1]
    if (!first) { event.preventDefault(); return }
    if (event.shiftKey && (document.activeElement === first || document.activeElement === event.currentTarget)) {
      event.preventDefault(); last.focus()
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault(); first.focus()
    }
  }
  const chooseCode = (channel: 'sms' | 'email') => {
    session.setSelectedChannel(channel)
    session.setAccount('')
    session.setCode('')
    session.selectTab('code')
    setChoosing(false)
  }
  const title = choosing ? '登录或创建账户' : session.tab === 'code'
    ? (session.selectedChannel === 'sms' ? '使用手机号继续' : session.selectedChannel === 'email' ? '使用邮箱继续' : '验证码登录')
    : TITLE_MAP[session.tab]
  const {
    tab,
    selectTab,
    codeLoginPresentation,
    wechatLoginEnabled
  } = session

  function handleOverlayClick(e: React.MouseEvent<HTMLDivElement>) {
    if (e.target === e.currentTarget) closeLoginModal()
  }

  function handleForgotPassword() {
    if (!codeLoginPresentation.enabled) {
      message.warning(t('验证码登录未开启，无法找回密码'))
      return
    }
    enterForgotPasswordFlow(router)
  }

  return (
    <div
      className={LOGIN_MODAL_OVERLAY_CLASS}
      data-state={visible ? 'open' : 'closed'}
      role="presentation"
      onClick={handleOverlayClick}
    >
      <div
        className={LOGIN_MODAL_FIT_SHELL_CLASS}
        onClick={(e) => e.stopPropagation()}
      >
      <div
        className={LOGIN_MODAL_FRAME_CLASS + ' login-modal-frame--compact'}
        ref={dialogRef}
        tabIndex={-1}
        onKeyDown={trapFocus}
        role="dialog"
        aria-modal="true"
        aria-label={t(title)}
        onClick={(e) => e.stopPropagation()}
      >
        <section className={LOGIN_MODAL_FORM_CLASS}>
          <ConfigProvider theme={LOGIN_MODAL_ANTD_THEME} wave={{ disabled: true }}>
          <button
            type="button"
            aria-label={t('关闭')}
            className={LOGIN_MODAL_CLOSE_CLASS}
            onClick={() => closeLoginModal()}
          >
            <svg viewBox="0 0 24 24" className="h-8 w-8" fill="none" aria-hidden="true">
              <path d="M6 6l12 12M18 6L6 18" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
            </svg>
          </button>

          <div
            id="login-captcha-box"
            className="absolute left-1/2 top-28 z-20 -translate-x-1/2"
            aria-hidden="true"
          />

          <div className={'login-form-body ' + LOGIN_MODAL_BODY_CLASS}>
            <div className={'login-form-brand ' + LOGIN_MODAL_BRAND_CLASS}>
              <span className="text-4xl font-bold leading-tight text-white">{creatorBrandName(locale)}</span>
              <h2 className={LOGIN_MODAL_TITLE_CLASS}>{t(title)}</h2>
              <p className="login-entry-description">{t(choosing ? '选择一种方式，开始你的创作' : tab === 'code' ? '验证后自动登录，新用户将创建账户' : '欢迎回来，继续你的创作')}</p>
            </div>
            {choosing ? (
              <div className="login-entry-methods">
                {!session.configLoaded && <p role="status">{t('正在加载登录方式…')}</p>}
                {session.smsLoginEnabled && <button type="button" className="login-entry-method" onClick={() => chooseCode('sms')}><span aria-hidden="true">＋86</span>{t('使用手机号继续')}</button>}
                {session.emailLoginEnabled && <button type="button" className="login-entry-method" onClick={() => chooseCode('email')}><span aria-hidden="true">@</span>{t('使用邮箱继续')}</button>}
                {wechatLoginEnabled && <button type="button" className="login-entry-method" onClick={() => { selectTab('wechat'); setChoosing(false) }}>{t('微信扫码登录')}</button>}
                {session.configLoaded && !session.smsLoginEnabled && !session.emailLoginEnabled && !wechatLoginEnabled && <p className="login-entry-description" role="status">{t('注册暂未开放，已有账户可使用密码登录')}</p>}
                <button type="button" className="login-entry-password" onClick={() => { selectTab('password'); setChoosing(false) }}>{t('已有账户？密码登录')}</button>
                <LoginAgreementHint termsOfServiceUrl={session.termsOfServiceUrl} privacyPolicyUrl={session.privacyPolicyUrl} />
              </div>
            ) : <div className="flex w-full justify-center">
              {tab === 'code' && (
                <LoginCodePanel
                  account={session.account}
                  code={session.code}
                  accountPlaceholder={codeLoginPresentation.accountPlaceholder}
                  registrationHint={codeLoginPresentation.registrationHint}
                  loading={session.loading}
                  sendCodeLoading={session.sendCodeLoading}
                  sendCodeCountdown={session.sendCodeCountdown}
                  codeMaxLength={session.loginCodeMaxLength}
                  termsOfServiceUrl={session.termsOfServiceUrl}
                  privacyPolicyUrl={session.privacyPolicyUrl}
                  onAccountChange={session.setAccount}
                  onCodeChange={session.setCode}
                  onSendCode={() => void session.handleSendCode()}
                  onSubmit={() => void session.handleSubmit()}
                  onUnlockAutofill={session.clearLoginInputReadonly}
                />
              )}
              {tab === 'password' && (
                <LoginPasswordPanel
                  account={session.account}
                  password={session.password}
                  loading={session.loading}
                  showRegister={codeLoginPresentation.enabled}
                  termsOfServiceUrl={session.termsOfServiceUrl}
                  privacyPolicyUrl={session.privacyPolicyUrl}
                  onAccountChange={session.setAccount}
                  onPasswordChange={session.setPassword}
                  onSubmit={() => void session.handleSubmit()}
                  onRegister={() => setChoosing(true)}
                  onForgotPassword={handleForgotPassword}
                  onUnlockAutofill={session.clearLoginInputReadonly}
                />
              )}
              {tab === 'wechat' && (
                <LoginWechatPanel
                  state={session.wechat.state}
                  enabled={wechatLoginEnabled}
                  termsOfServiceUrl={session.termsOfServiceUrl}
                  privacyPolicyUrl={session.privacyPolicyUrl}
                  onRefresh={() => void session.wechat.openWechatLogin()}
                />
              )}
            </div>}
          </div>

          {!choosing && <button type="button" className="login-entry-back" onClick={() => setChoosing(true)}>{t('返回其他登录方式')}</button>}
          </ConfigProvider>
        </section>
      </div>
      </div>
    </div>
  )
}
