'use client'

import { message } from 'antd'
import { useCallback,useEffect,useRef,useState } from 'react'
import { useAuthPublicConfig } from '~/hooks/useAuthPublicConfig'
import { useBehaviorCaptcha } from '~/hooks/useBehaviorCaptcha'
import type { AccountCodeChannel,SendCodeRequest } from '~/types/business-api'
import { accountApiErrorMessage } from '~/utils/accountSecurity'
import { authSendCode } from '~/utils/businessApi'

type AccountCodeScene = Exclude<SendCodeRequest['scene'], 'login' | 'reset'>

export interface SendAccountCodeOptions {
  key: string
  scene: AccountCodeScene
  channel: AccountCodeChannel
  target?: string
}

export interface AccountVerificationCodeController {
  sendCode: (options: SendAccountCodeOptions) => Promise<boolean>
  isSending: (key: string) => boolean
  countdown: (key: string) => number
}

export function useAccountVerificationCode(): AccountVerificationCodeController {
  const { captchaEnabled,captchaType,getSendCodeIntervalSeconds } = useAuthPublicConfig()
  const { opening,openBehaviorCaptcha,destroyActive } = useBehaviorCaptcha()
  const [loadingKey, setLoadingKey] = useState('')
  const loadingKeyRef = useRef('')
  const [countdowns, setCountdowns] = useState<Record<string, number>>({})
  const countdownsRef = useRef(countdowns)
  countdownsRef.current = countdowns

  useEffect(() => {
    const timer = window.setInterval(() => {
      if (!Object.values(countdownsRef.current).some((value) => value > 0)) return
      setCountdowns((current) => {
        const next: Record<string, number> = {}
        for (const [key, value] of Object.entries(current)) {
          next[key] = Math.max(0, value - 1)
        }
        return next
      })
    }, 1000)
    return () => {
      window.clearInterval(timer)
      destroyActive()
    }
  }, [destroyActive])

  const sendCode = useCallback(
    async ({ key, scene, channel, target }: SendAccountCodeOptions): Promise<boolean> => {
      if (loadingKeyRef.current || opening || (countdownsRef.current[key] || 0) > 0) return false
      loadingKeyRef.current = key
      setLoadingKey(key)
      try {
        const execute = async (captchaToken = '') => {
          await authSendCode(
            {
              scene,
              codeType: channel,
              ...(target?.trim() ? { target: target.trim() } : {})
            },
            captchaToken || undefined
          )
        }
        if (captchaEnabled) {
          const result = await openBehaviorCaptcha({
            bindEl: '#account-settings-captcha-box',
            captchaType,
            onSuccess: execute
          })
          if (result.error) throw result.error
          if (!result.ok) return false
        } else {
          await execute()
        }
        setCountdowns((current) => ({
          ...current,
          [key]: getSendCodeIntervalSeconds(channel)
        }))
        message.success('验证码已发送')
        return true
      } catch (error) {
        message.error(accountApiErrorMessage(error, '发送验证码失败'))
        return false
      } finally {
        loadingKeyRef.current = ''
        setLoadingKey('')
      }
    },
    [captchaEnabled, captchaType, getSendCodeIntervalSeconds, openBehaviorCaptcha, opening]
  )

  return {
    sendCode,
    isSending: (key: string) => loadingKey === key || opening,
    countdown: (key: string) => countdowns[key] || 0
  }
}
