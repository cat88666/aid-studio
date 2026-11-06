'use client'

import { Button,Checkbox,Empty,Modal,Pagination,Spin,message } from 'antd'
import { useCallback,useEffect,useMemo,useRef,useState } from 'react'
import {
  ACCOUNT_SETTINGS_SUBMIT_BLOCK_CLASS,
  ACCOUNT_SETTINGS_SUBMIT_CLASS
} from '~/utils/accountSettingsLayout'
import type {
  AccountCodeChannel,
  AccountLoginHistoryData,
  AccountSecurityData,
  WechatBindCheckData
} from '~/types/business-api'
import { ChannelTabs, CodeField, Field, SectionIntro } from './AccountSettingsFields'
import {
  accountApiErrorMessage,
  getAccountContactCapability,
  getAccountVerificationChannels,
  validateAccountTarget
} from '~/utils/accountSecurity'
import {
  accountBind,
  accountCancel,
  accountLoginHistory,
  accountLogoutAll,
  accountLogoutOthers,
  accountPasswordChange,
  accountPasswordSet,
  accountRebind,
  accountUnbind,
  wechatBindCheck,
  wechatBindQrcode
} from '~/utils/businessApi'
import {
  authPasswordIssueMessage,
  validateAuthPasswordChange
} from '~/utils/authPasswordPolicy'
import type { AccountVerificationCodeController } from './useAccountVerificationCode'

interface SecuritySectionProps {
  security: AccountSecurityData
  codes: AccountVerificationCodeController
  refreshSecurity: () => Promise<void>
  onAuthInvalidated: () => void
}

export function AccountPasswordSection({
  security,
  codes,
  onAuthInvalidated
}: SecuritySectionProps) {
  const channels = useMemo(() => getAccountVerificationChannels(security), [security])
  const [channel, setChannel] = useState<AccountCodeChannel>(channels[0] || 'sms')
  const [oldPassword, setOldPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [code, setCode] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!channels.includes(channel) && channels[0]) setChannel(channels[0])
  }, [channel, channels])

  const submit = async () => {
    if (security.passwordSet && !oldPassword) {
      message.error('请输入当前密码')
      return
    }
    if (!security.passwordSet && (!channels.length || !code)) {
      message.error(channels.length ? '请输入验证码' : '请先绑定手机号或邮箱')
      return
    }
    const issue = validateAuthPasswordChange({
      newPassword,
      confirmPassword,
      oldPassword: security.passwordSet ? oldPassword : undefined
    })
    if (issue) {
      message.error(authPasswordIssueMessage(issue))
      return
    }
    setSubmitting(true)
    try {
      if (security.passwordSet) {
        await accountPasswordChange({ oldPassword, newPassword, confirmPassword })
      } else {
        await accountPasswordSet({ verifyType: channel, code, newPassword, confirmPassword })
      }
      message.success(security.passwordSet ? '密码修改成功，请重新登录' : '密码设置成功，请重新登录')
      onAuthInvalidated()
    } catch (error) {
      message.error(accountApiErrorMessage(error, security.passwordSet ? '修改密码失败' : '设置密码失败'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section>
      <SectionIntro
        title={security.passwordSet ? '修改密码' : '设置登录密码'}
        description={security.passwordSet ? '修改后所有设备需要重新登录。' : '当前账号尚未设置密码，设置后可使用账号密码登录。'}
      />
      <div className="account-settings__form-card">
        {security.passwordSet ? (
          <Field label="当前密码" value={oldPassword} onChange={setOldPassword} type="password" autoComplete="current-password" placeholder="请输入当前密码" maxLength={20} />
        ) : channels.length ? (
          <>
            <ChannelTabs channels={channels} value={channel} onChange={setChannel} />
            <CodeField
              value={code}
              onChange={setCode}
              buttonKey={`set-password-${channel}`}
              codes={codes}
              onSend={() => void codes.sendCode({ key: `set-password-${channel}`, scene: 'set_password', channel })}
            />
          </>
        ) : (
          <div className="account-settings__notice">请先在“手机号或邮箱”中绑定一种验证方式。</div>
        )}
        <Field label="新密码" value={newPassword} onChange={setNewPassword} type="password" autoComplete="new-password" placeholder="请输入新密码" maxLength={20} />
        <Field label="确认新密码" value={confirmPassword} onChange={setConfirmPassword} type="password" autoComplete="new-password" placeholder="请再次输入新密码" maxLength={20} />
        <p className="account-settings__field-hint">密码须为 5-20 位，且同时包含大写字母、小写字母和数字</p>
        <Button className={ACCOUNT_SETTINGS_SUBMIT_BLOCK_CLASS} type="primary" loading={submitting} onClick={() => void submit()}>
          {security.passwordSet ? '确认修改' : '设置密码'}
        </Button>
      </div>
    </section>
  )
}

type ContactMode = 'view' | 'bind' | 'rebind' | 'unbind'

function ContactChannelCard({
  security,
  channel,
  codes,
  refreshSecurity
}: Pick<SecuritySectionProps, 'security' | 'codes' | 'refreshSecurity'> & {
  channel: AccountCodeChannel
}) {
  const capability = getAccountContactCapability(security, channel)
  const [mode, setMode] = useState<ContactMode>(capability.bound ? 'view' : 'bind')
  const [target, setTarget] = useState('')
  const [code, setCode] = useState('')
  const [oldCode, setOldCode] = useState('')
  const [newCode, setNewCode] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const prefix = `contact-${channel}`

  useEffect(() => {
    setMode(capability.bound ? 'view' : 'bind')
    setTarget('')
    setCode('')
    setOldCode('')
    setNewCode('')
  }, [capability.bound])

  const ensureTarget = () => {
    if (!validateAccountTarget(target, channel)) {
      message.error(channel === 'sms' ? '手机号格式不正确' : '邮箱格式不正确')
      return false
    }
    return true
  }

  const submit = async () => {
    if (mode === 'bind' && (!ensureTarget() || !code)) {
      if (!code && target) message.error('请输入验证码')
      return
    }
    if (mode === 'rebind' && (!ensureTarget() || !oldCode || !newCode)) {
      if (target && (!oldCode || !newCode)) message.error('请填写旧地址和新地址收到的验证码')
      return
    }
    if (mode === 'unbind' && !code) {
      message.error('请输入验证码')
      return
    }
    setSubmitting(true)
    try {
      if (mode === 'bind') await accountBind({ bindType: channel, target: target.trim(), code })
      if (mode === 'rebind') await accountRebind({ bindType: channel, newTarget: target.trim(), oldCode, newCode })
      if (mode === 'unbind') await accountUnbind({ unbindType: channel, code })
      message.success(mode === 'bind' ? `${capability.label}绑定成功` : mode === 'rebind' ? `${capability.label}换绑成功` : `${capability.label}解绑成功`)
      await refreshSecurity()
    } catch (error) {
      message.error(accountApiErrorMessage(error, `${capability.label}操作失败`))
    } finally {
      setSubmitting(false)
    }
  }

  if (!capability.available && !capability.bound) {
    return (
      <article className="account-settings__contact-card is-disabled">
        <div><strong>{capability.label}</strong><span>当前服务未开放</span></div>
      </article>
    )
  }

  return (
    <article className="account-settings__contact-card">
      <div className="account-settings__contact-head">
        <div>
          <strong>{capability.label}</strong>
          <span>{capability.bound ? capability.maskedValue || '已绑定' : '尚未绑定'}</span>
        </div>
        <span className={capability.bound ? 'account-settings__status is-bound' : 'account-settings__status'}>
          {capability.bound ? '已绑定' : '未绑定'}
        </span>
      </div>

      {mode === 'view' ? (
        <div className="account-settings__inline-actions">
          <Button disabled={!capability.available} onClick={() => setMode('rebind')}>换绑{capability.label}</Button>
          <Button danger disabled={!capability.available || !capability.canUnbind} title={!capability.available ? '当前渠道服务未开放' : capability.canUnbind ? '' : '请至少保留一种登录方式'} onClick={() => setMode('unbind')}>
            解绑
          </Button>
          {!capability.available ? <span className="text-xs text-[var(--home-muted,#8e97a5)]">当前渠道服务未开放</span> : null}
        </div>
      ) : (
        <div className="account-settings__contact-form">
          {(mode === 'bind' || mode === 'rebind') && (
            <Field label={mode === 'bind' ? `新${capability.label}` : `换绑后的${capability.label}`} value={target} onChange={setTarget} placeholder={channel === 'sms' ? '请输入手机号' : '请输入邮箱'} maxLength={100} />
          )}
          {mode === 'rebind' && (
            <CodeField label={`原${capability.label}验证码`} value={oldCode} onChange={setOldCode} buttonKey={`${prefix}-old`} codes={codes} onSend={() => void codes.sendCode({ key: `${prefix}-old`, scene: 'rebind_old', channel })} />
          )}
          {mode === 'rebind' && (
            <CodeField label={`新${capability.label}验证码`} value={newCode} onChange={setNewCode} buttonKey={`${prefix}-new`} codes={codes} onSend={() => {
              if (ensureTarget()) void codes.sendCode({ key: `${prefix}-new`, scene: 'rebind_new', channel, target })
            }} />
          )}
          {mode === 'bind' && (
            <CodeField value={code} onChange={setCode} buttonKey={`${prefix}-bind`} codes={codes} onSend={() => {
              if (ensureTarget()) void codes.sendCode({ key: `${prefix}-bind`, scene: 'bind', channel, target })
            }} />
          )}
          {mode === 'unbind' && (
            <>
              <div className="account-settings__notice">验证码将发送至当前绑定的{capability.label} {capability.maskedValue}</div>
              <CodeField value={code} onChange={setCode} buttonKey={`${prefix}-unbind`} codes={codes} onSend={() => void codes.sendCode({ key: `${prefix}-unbind`, scene: 'unbind', channel })} />
            </>
          )}
          <div className="account-settings__inline-actions">
            <Button className={ACCOUNT_SETTINGS_SUBMIT_CLASS} type="primary" loading={submitting} onClick={() => void submit()}>{mode === 'bind' ? '确认绑定' : mode === 'rebind' ? '确认换绑' : '确认解绑'}</Button>
            {capability.bound && <Button onClick={() => setMode('view')}>取消</Button>}
          </div>
        </div>
      )}
    </article>
  )
}

export function AccountContactSection(props: SecuritySectionProps) {
  return (
    <section>
      <SectionIntro title="手机号或邮箱" description="系统会根据当前绑定状态自动提供绑定、换绑或解绑操作。" />
      <div className="account-settings__card-list">
        <ContactChannelCard {...props} channel="sms" />
        <ContactChannelCard {...props} channel="email" />
      </div>
    </section>
  )
}

type WechatBindUiState = 'idle' | 'loading' | 'waiting' | 'scanned' | 'expired' | 'failed'

function readWechatCheck(input: unknown): { data?: WechatBindCheckData; msg?: string } {
  return (input || {}) as { data?: WechatBindCheckData; msg?: string }
}

export function AccountWechatSection({ security, refreshSecurity }: SecuritySectionProps) {
  const [qrUrl, setQrUrl] = useState('')
  const [state, setState] = useState<WechatBindUiState>('idle')
  const [statusText, setStatusText] = useState('')
  const sessionRef = useRef(0)
  const timerRef = useRef<number | null>(null)

  const stop = useCallback(() => {
    sessionRef.current += 1
    if (timerRef.current) window.clearInterval(timerRef.current)
    timerRef.current = null
  }, [])

  const applyCheck = useCallback(async (input: unknown) => {
    const payload = readWechatCheck(input)
    const status = String(payload.data?.status || '').toUpperCase()
    if (status === 'WAITING') {
      setState('waiting')
      setStatusText(payload.msg || '请使用微信扫码')
    } else if (status === 'SCANNED') {
      setState('scanned')
      setStatusText(payload.msg || '已扫码，绑定处理中')
    } else if (status === 'SUCCESS') {
      stop()
      setStatusText('微信绑定成功')
      message.success('微信绑定成功')
      await refreshSecurity()
    } else if (status === 'EXPIRED') {
      stop()
      setState('expired')
      setStatusText(payload.msg || '二维码已过期')
    } else if (status === 'FAIL') {
      stop()
      setState('failed')
      setStatusText(payload.msg || '绑定失败，请刷新重试')
    }
  }, [refreshSecurity, stop])

  const loadQr = useCallback(async () => {
    if (!security.wechatAvailable || security.wechatBound) return
    stop()
    const session = sessionRef.current
    setState('loading')
    setQrUrl('')
    setStatusText('正在获取二维码')
    try {
      const data = await wechatBindQrcode()
      if (session !== sessionRef.current) return
      setQrUrl(data.qrCodeUrl)
      setState('waiting')
      setStatusText('请使用微信扫码绑定')
      let pending = false
      timerRef.current = window.setInterval(async () => {
        if (pending || session !== sessionRef.current) return
        pending = true
        try {
          await applyCheck(await wechatBindCheck(data.sceneStr))
        } catch (error) {
          await applyCheck(error)
        } finally {
          pending = false
        }
      }, 2000)
    } catch (error) {
      setState('failed')
      setStatusText(accountApiErrorMessage(error, '获取微信二维码失败'))
    }
  }, [applyCheck, security.wechatAvailable, security.wechatBound, stop])

  useEffect(() => {
    if (!security.wechatBound) void loadQr()
    return stop
  }, [loadQr, security.wechatBound, stop])

  return (
    <section>
      <SectionIntro title="微信绑定" description="使用微信扫描二维码，将当前账号与微信安全绑定。" />
      <div className="account-settings__form-card account-settings__wechat-card">
        {security.wechatBound ? (
          <div className="account-settings__wechat-bound"><span className="account-settings__success-dot" />微信已绑定</div>
        ) : !security.wechatAvailable ? (
          <div className="account-settings__notice">微信绑定服务暂未开放。</div>
        ) : (
          <>
            <div className="account-settings__qr">
              {qrUrl ? <img src={qrUrl} alt="微信绑定二维码" /> : <Spin />}
              {qrUrl && (state === 'expired' || state === 'failed') && (
                <button type="button" onClick={() => void loadQr()}>{state === 'expired' ? '二维码已过期，点击刷新' : '绑定失败，点击刷新'}</button>
              )}
              {qrUrl && state === 'scanned' && <div>已扫码，绑定处理中</div>}
            </div>
            <p className="account-settings__qr-status">{statusText}</p>
          </>
        )}
      </div>
    </section>
  )
}

export function AccountSessionsSection({ onAuthInvalidated }: SecuritySectionProps) {
  const [loading, setLoading] = useState<'others' | 'all' | ''>('')

  const logoutOthers = () => {
    Modal.confirm({
      className: 'home-confirm-modal',
      wrapClassName: 'create-flow-modal home-confirm-wrap',
      title: '确认退出其他设备',
      content: '当前设备会保持登录，其他设备上的登录会话将全部退出。',
      okText: '确认退出',
      cancelText: '取消',
      centered: true,
      onOk: async () => {
        setLoading('others')
        try {
          const count = await accountLogoutOthers()
          message.success(count > 0 ? `已退出 ${count} 个其他设备` : '当前没有其他在线设备')
        } catch (error) {
          message.error(accountApiErrorMessage(error, '退出其他设备失败'))
          throw error
        } finally {
          setLoading('')
        }
      }
    })
  }

  const logoutAll = () => {
    Modal.confirm({
      className: 'home-confirm-modal',
      wrapClassName: 'create-flow-modal home-confirm-wrap',
      title: '确认退出全部设备',
      content: '包括当前设备在内的全部登录会话都会退出，需要重新登录。',
      okText: '退出全部设备',
      okButtonProps: { danger: true },
      cancelText: '取消',
      centered: true,
      onOk: async () => {
        setLoading('all')
        try {
          await accountLogoutAll()
          message.success('已退出全部设备')
          onAuthInvalidated()
        } catch (error) {
          message.error(accountApiErrorMessage(error, '退出全部设备失败'))
          throw error
        } finally {
          setLoading('')
        }
      }
    })
  }

  return (
    <section>
      <SectionIntro title="设备登录管理" description="管理当前账号在其他设备上的登录状态。" />
      <div className="account-settings__card-list">
        <article className="account-settings__action-card">
          <div><strong>退出其他设备</strong><span>保留当前设备，仅退出其他设备上的登录会话。</span></div>
          <Button loading={loading === 'others'} onClick={logoutOthers}>退出其他设备</Button>
        </article>
        <article className="account-settings__action-card">
          <div><strong>退出全部设备</strong><span>包括当前设备，操作完成后需要重新登录。</span></div>
          <Button danger loading={loading === 'all'} onClick={logoutAll}>退出全部设备</Button>
        </article>
      </div>
    </section>
  )
}

export function AccountLoginHistorySection() {
  const [data, setData] = useState<AccountLoginHistoryData>({ total: 0, pageNum: 1, pageSize: 10, list: [] })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const load = useCallback(async (pageNum: number) => {
    setLoading(true)
    setError('')
    try {
      const result = await accountLoginHistory({ pageNum, pageSize: 10 })
      setData({ ...result, list: Array.isArray(result.list) ? result.list : [] })
    } catch (err) {
      setError(accountApiErrorMessage(err, '登录历史加载失败'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { void load(1) }, [load])

  return (
    <section>
      <SectionIntro title="登录历史" description="查看当前账号最近的登录结果、设备和位置。" />
      <Spin spinning={loading}>
        {error ? (
          <div className="account-settings__load-error"><span>{error}</span><Button onClick={() => void load(data.pageNum || 1)}>重新加载</Button></div>
        ) : !data.list.length ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无登录记录" />
        ) : (
          <div className="account-settings__history-list">
            {data.list.map((item) => {
              const success = item.status === '0' || item.status.toLowerCase() === 'success'
              return (
                <article key={item.id}>
                  <div className="account-settings__history-top">
                    <strong>{item.browser || '未知浏览器'} · {item.os || '未知系统'}</strong>
                    <span className={success ? 'account-settings__status is-bound' : 'account-settings__status is-failed'}>{success ? '成功' : '失败'}</span>
                  </div>
                  <div className="account-settings__history-meta"><span>{item.loginTime || '—'}</span><span>{item.loginLocation || '未知位置'} · {item.ipaddr || '—'}</span></div>
                  {item.message ? <p>{item.message}</p> : null}
                </article>
              )
            })}
          </div>
        )}
      </Spin>
      {data.total > data.pageSize && <Pagination className="account-settings__pagination" current={data.pageNum} pageSize={data.pageSize} total={data.total} showSizeChanger={false} onChange={(page) => void load(page)} />}
    </section>
  )
}

export function AccountCancelSection({ security, codes, onAuthInvalidated }: SecuritySectionProps) {
  const channels = useMemo(() => getAccountVerificationChannels(security), [security])
  const [channel, setChannel] = useState<AccountCodeChannel>(channels[0] || 'sms')
  const [code, setCode] = useState('')
  const [acknowledged, setAcknowledged] = useState(false)

  useEffect(() => {
    if (!channels.includes(channel) && channels[0]) setChannel(channels[0])
  }, [channel, channels])

  const submit = () => {
    if (!code) {
      message.error('请输入验证码')
      return
    }
    Modal.confirm({
      className: 'home-confirm-modal',
      wrapClassName: 'create-flow-modal home-confirm-wrap',
      title: '最后确认注销账号',
      content: '账号注销后不可恢复，全部设备会立即退出登录。',
      okText: '确认注销',
      okButtonProps: { danger: true },
      cancelText: '取消',
      centered: true,
      onOk: async () => {
        try {
          await accountCancel({ verifyType: channel, code })
          message.success('账号已注销')
          onAuthInvalidated()
        } catch (error) {
          message.error(accountApiErrorMessage(error, '注销账号失败'))
          throw error
        }
      }
    })
  }

  return (
    <section>
      <SectionIntro title="注销账号" description="注销会永久删除账号并清理全部登录会话，此操作不可恢复。" />
      <div className="account-settings__form-card account-settings__danger-card">
        {security.reRegistrationRestrictionEnabled && (
          <div className="account-settings__danger-notice">注销后，原绑定身份在 {security.reRegistrationRestrictionDays} 天内无法重新注册。</div>
        )}
        {channels.length ? (
          <>
            <ChannelTabs channels={channels} value={channel} onChange={setChannel} />
            <CodeField value={code} onChange={setCode} buttonKey={`cancel-${channel}`} codes={codes} onSend={() => void codes.sendCode({ key: `cancel-${channel}`, scene: 'cancel', channel })} />
            <Checkbox checked={acknowledged} onChange={(event) => setAcknowledged(event.target.checked)}>我已知晓注销后账号无法恢复</Checkbox>
            <Button danger className="account-settings__danger-submit account-settings__submit--block" disabled={!acknowledged} onClick={submit}>注销账号</Button>
          </>
        ) : (
          <div className="account-settings__notice">当前账号没有可用的手机或邮箱验证方式，暂时无法在线注销。</div>
        )}
      </div>
    </section>
  )
}
