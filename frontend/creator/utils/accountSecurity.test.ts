import { describe, expect, it } from 'vitest'
import type { AccountSecurityData } from '~/types/business-api'
import {
  getAccountContactCapability,
  getAccountVerificationChannels,
  getWechatUnbindChannel,
  validateAccountTarget
} from './accountSecurity'

const base: AccountSecurityData = {
  passwordSet: true,
  phoneBound: true,
  maskedPhone: '138****8888',
  emailBound: false,
  wechatBound: true,
  loginMethods: ['sms', 'password', 'wechat'],
  canUnbindPhone: false,
  canUnbindEmail: false,
  canUnbindWechat: true,
  smsAvailable: true,
  emailAvailable: true,
  wechatAvailable: true,
  reRegistrationRestrictionEnabled: true,
  reRegistrationRestrictionDays: 15,
  passwordPolicy: { minLength: 5, maxLength: 20 }
}

describe('accountSecurity helpers', () => {
  it('根据绑定状态决定联系方式展示绑定还是管理操作', () => {
    expect(getAccountContactCapability(base, 'sms')).toMatchObject({
      operation: 'manage',
      bound: true,
      maskedValue: '138****8888',
      canUnbind: false
    })
    expect(getAccountContactCapability(base, 'email')).toMatchObject({
      operation: 'bind',
      bound: false
    })
  })

  it('验证码渠道只返回已绑定且服务端可用的渠道，并优先手机号', () => {
    expect(getAccountVerificationChannels(base)).toEqual(['sms'])
    expect(getWechatUnbindChannel(base)).toBe('sms')
  })

  it('按手机号和邮箱各自格式校验新绑定地址', () => {
    expect(validateAccountTarget('13888888888', 'sms')).toBe(true)
    expect(validateAccountTarget('bad-phone', 'sms')).toBe(false)
    expect(validateAccountTarget('user@example.com', 'email')).toBe(true)
    expect(validateAccountTarget('user@', 'email')).toBe(false)
  })
})
