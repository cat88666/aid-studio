'use client'

import { AuthDarkField } from '~/components/auth/AuthDarkField'
import { unlockAuthInputAutofill } from '~/utils/authUnlockAutofill'
import type { AccountCodeChannel } from '~/types/business-api'
import type { AccountVerificationCodeController } from './useAccountVerificationCode'

export function SectionIntro({ title, description }: { title: string; description: string }) {
  return (
    <header className="account-settings__section-head">
      <h3>{title}</h3>
      <p>{description}</p>
    </header>
  )
}

export function Field({
  label,
  value,
  onChange,
  placeholder,
  type = 'text',
  maxLength,
  autoComplete
}: {
  label: string
  value: string
  onChange: (value: string) => void
  placeholder: string
  type?: 'text' | 'password'
  maxLength?: number
  autoComplete?: string
}) {
  return (
    <label className="account-settings__field">
      <span>{label}</span>
      <AuthDarkField
        type={type}
        value={value}
        placeholder={placeholder}
        maxLength={maxLength}
        autoComplete={autoComplete}
        surface="solid"
        showPasswordToggle={type === 'password'}
        onChange={onChange}
        onUnlockAutofill={unlockAuthInputAutofill}
      />
    </label>
  )
}

export function CodeField({
  label = '验证码',
  value,
  onChange,
  buttonKey,
  codes,
  onSend
}: {
  label?: string
  value: string
  onChange: (value: string) => void
  buttonKey: string
  codes: AccountVerificationCodeController
  onSend: () => void
}) {
  const seconds = codes.countdown(buttonKey)
  const sending = codes.isSending(buttonKey)
  return (
    <label className="account-settings__field">
      <span>{label}</span>
      <AuthDarkField
        value={value}
        placeholder="请输入验证码"
        maxLength={8}
        inputMode="numeric"
        autoComplete="one-time-code"
        surface="solid"
        onChange={(next) => onChange(next.replace(/\D/g, ''))}
        onUnlockAutofill={unlockAuthInputAutofill}
        suffix={
          <button
            type="button"
            disabled={sending || seconds > 0}
            className="account-settings__code-btn"
            onClick={onSend}
          >
            {seconds > 0 ? `${seconds}s` : sending ? '发送中' : '发送验证码'}
          </button>
        }
      />
    </label>
  )
}

export function ChannelTabs({
  channels,
  value,
  onChange
}: {
  channels: AccountCodeChannel[]
  value: AccountCodeChannel
  onChange: (value: AccountCodeChannel) => void
}) {
  return (
    <div className="account-settings__channel-tabs" role="tablist" aria-label="验证方式">
      {channels.map((channel) => (
        <button
          key={channel}
          type="button"
          className={value === channel ? 'is-active' : ''}
          role="tab"
          aria-selected={value === channel}
          onClick={() => onChange(channel)}
        >
          {channel === 'sms' ? '手机验证' : '邮箱验证'}
        </button>
      ))}
    </div>
  )
}
