'use client'

import { useEffect, useRef, useState } from 'react'
import type { LoginData } from '~/types/business-api'
import { useAuthText } from '~/hooks/useAuthText'
import { useCreatorLocale } from '~/hooks/useCreatorLocale'
import { authGoogleChallenge, authLogin } from '~/utils/businessApi'
import { loadGoogleIdentity } from '~/utils/googleIdentity'

interface Props {
  enabled: boolean
  clientId: string
  inviteCode: string
  onSuccess: (data: LoginData) => void
}

export function GoogleLoginButton({ enabled, clientId, inviteCode, onSuccess }: Props) {
  const t = useAuthText()
  const { locale } = useCreatorLocale()
  const container = useRef<HTMLDivElement>(null)
  const latest = useRef({ inviteCode, onSuccess })
  latest.current = { inviteCode, onSuccess }
  const [attempt, setAttempt] = useState(0)
  const [status, setStatus] = useState<'loading' | 'ready' | 'signing' | 'error'>('loading')
  const [error, setError] = useState('')

  useEffect(() => {
    if (!enabled || !clientId) return
    let active = true
    let submitting = false
    let timer: ReturnType<typeof setTimeout> | undefined
    const target = container.current!
    target.replaceChildren()
    setStatus('loading')
    const fail = (text: string) => {
      if (!active) return
      clearTimeout(timer)
      target.replaceChildren()
      setError(text)
      setStatus('error')
    }
    void (async () => {
      try {
        const identity = await loadGoogleIdentity()
        if (!active) return
        const challenge = await authGoogleChallenge()
        if (!active) return
        if (!challenge.nonce || !Number.isFinite(challenge.expiresIn) || challenge.expiresIn <= 0) throw new Error()
        const expiresAt = Date.now() + challenge.expiresIn * 1000
        identity.initialize({
          client_id: clientId,
          nonce: challenge.nonce,
          ux_mode: 'popup',
          auto_select: false,
          callback: (response) => {
            if (!active || submitting) return
            if (Date.now() >= expiresAt) { fail('Google 登录已过期，请重试'); return }
            if (!response.credential) { fail('Google 登录暂时不可用，请重试或选择其他方式'); return }
            submitting = true
            clearTimeout(timer)
            setStatus('signing')
            target.replaceChildren()
            void authLogin({ loginType: 'google', credential: response.credential, googleNonce: challenge.nonce, inviteCode: latest.current.inviteCode })
              .then((data) => { if (active) latest.current.onSuccess(data) })
              .catch((e: unknown) => {
                const text = (e as { msg?: string; message?: string })?.msg || (e as Error)?.message
                fail(text || 'Google 登录暂时不可用，请重试或选择其他方式')
              })
          }
        })
        identity.renderButton(target, {
          type: 'standard', theme: 'outline', size: 'large', text: 'continue_with',
          width: Math.min(400, Math.max(200, target.clientWidth)),
          locale: locale === 'zh-hant' ? 'zh-TW' : locale === 'zh' ? 'zh-CN' : locale
        })
        setStatus('ready')
        timer = setTimeout(() => fail('Google 登录已过期，请重试'), challenge.expiresIn * 1000)
      } catch {
        fail('Google 登录暂时不可用，请重试或选择其他方式')
      }
    })()
    return () => { active = false; clearTimeout(timer); target.replaceChildren() }
  }, [enabled, clientId, locale, attempt])

  if (!enabled || !clientId) return null
  return <div className="login-google" aria-busy={status === 'loading' || status === 'signing'}>
    <div ref={container} className="login-google-button" />
    {status === 'loading' && <p role="status">{t('正在加载 Google 登录…')}</p>}
    {status === 'signing' && <p role="status">{t('登录中...')}</p>}
    {status === 'error' && <><p role="alert">{t(error)}</p><button type="button" className="login-entry-method" onClick={() => setAttempt((value) => value + 1)}>{t('重试 Google 登录')}</button></>}
  </div>
}
