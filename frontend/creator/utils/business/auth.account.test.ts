import { beforeEach,describe,expect,it,vi } from 'vitest'

const requestMock = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn()
}))

vi.mock('~/utils/api', () => ({ request: requestMock }))

import {
  accountLoginHistory,
  accountLogoutAll,
  accountPasswordChange,
  accountRebind,
  accountSecurity
} from './auth'

describe('account security API wrappers', () => {
  beforeEach(() => {
    requestMock.get.mockReset()
    requestMock.post.mockReset()
  })

  it('读取账号安全状态并解包 data', async () => {
    const security = { passwordSet: true, phoneBound: true }
    requestMock.post.mockResolvedValue({ code: 200, msg: 'ok', data: security })

    await expect(accountSecurity()).resolves.toBe(security)
    expect(requestMock.post).toHaveBeenCalledWith('/api/user/account/security', {})
  })

  it('换绑时原样提交新地址和两组验证码', async () => {
    requestMock.post.mockResolvedValue({ code: 200, msg: 'ok' })
    const body = {
      bindType: 'email' as const,
      newTarget: 'new@example.com',
      oldCode: '123456',
      newCode: '654321'
    }

    await accountRebind(body)
    expect(requestMock.post).toHaveBeenCalledWith('/api/user/account/rebind', body)
  })

  it('改密、退出全部设备和登录历史使用文档约定接口', async () => {
    requestMock.post
      .mockResolvedValueOnce({ code: 200, msg: 'ok' })
      .mockResolvedValueOnce({ code: 200, msg: 'ok', data: 3 })
      .mockResolvedValueOnce({
        code: 200,
        msg: 'ok',
        data: { total: 0, pageNum: 1, pageSize: 10, list: [] }
      })

    await accountPasswordChange({
      oldPassword: 'Old12',
      newPassword: 'New12',
      confirmPassword: 'New12'
    })
    await expect(accountLogoutAll()).resolves.toBe(3)
    await accountLoginHistory({ pageNum: 1, pageSize: 10 })

    expect(requestMock.post.mock.calls.map((call) => call[0])).toEqual([
      '/api/user/account/password/change',
      '/api/user/account/session/logout-all',
      '/api/user/account/login-history'
    ])
  })
})
