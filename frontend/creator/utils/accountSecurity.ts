import type { AccountCodeChannel, AccountSecurityData } from '~/types/business-api'
import { isValidCodeLoginTarget } from '~/utils/authLoginMethods'

export interface AccountContactCapability {
  channel: AccountCodeChannel
  label: string
  bound: boolean
  maskedValue: string
  available: boolean
  canUnbind: boolean
  operation: 'bind' | 'manage' | 'unavailable'
}

export function getAccountContactCapability(
  security: AccountSecurityData,
  channel: AccountCodeChannel
): AccountContactCapability {
  const isPhone = channel === 'sms'
  const bound = isPhone ? security.phoneBound : security.emailBound
  const available = isPhone ? security.smsAvailable : security.emailAvailable
  return {
    channel,
    label: isPhone ? '手机号' : '邮箱',
    bound,
    maskedValue: (isPhone ? security.maskedPhone : security.maskedEmail) || '',
    available,
    canUnbind: isPhone ? security.canUnbindPhone : security.canUnbindEmail,
    operation: bound ? 'manage' : available ? 'bind' : 'unavailable'
  }
}

export function getAccountVerificationChannels(
  security: AccountSecurityData
): AccountCodeChannel[] {
  const channels: AccountCodeChannel[] = []
  if (security.phoneBound && security.smsAvailable) channels.push('sms')
  if (security.emailBound && security.emailAvailable) channels.push('email')
  return channels
}

export function getWechatUnbindChannel(
  security: AccountSecurityData
): AccountCodeChannel | null {
  return getAccountVerificationChannels(security)[0] || null
}

export function validateAccountTarget(target: string, channel: AccountCodeChannel): boolean {
  return isValidCodeLoginTarget(target, channel)
}

export function accountApiErrorMessage(error: unknown, fallback: string): string {
  const value = error as { msg?: string; message?: string }
  return String(value?.msg ?? value?.message ?? fallback)
}
