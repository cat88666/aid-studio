// @vitest-environment jsdom
import { beforeEach, expect, it, vi } from 'vitest'
import { mapUserInfoFromApiToUser } from './userProfile'

vi.mock('~/utils/businessApi', () => ({ userProfile: vi.fn(), userBalance: vi.fn() }))
import { useUserStore } from '~/stores/user'

beforeEach(() => {
  localStorage.clear()
  useUserStore.setState({ user: null, token: '' })
})

it('keeps every digit of a public Snowflake ID through mapping and browser storage', () => {
  const id = '9007199254740993123'
  const user = mapUserInfoFromApiToUser({ userId: id, userName: 'internal-login', nickName: `Sakura_${id}` })
  useUserStore.getState().setUser(user)
  useUserStore.setState({ user: null })
  useUserStore.getState().hydrateFromStorage()
  expect(useUserStore.getState().user?.id).toBe(id)
  expect(useUserStore.getState().user?.nickName).toBe(`Sakura_${id}`)
})

it('keeps a legacy cache for recovery without rendering its old nickname', () => {
  const raw = JSON.stringify({ id: '3', nickName: 'Sakura_196cUq' })
  localStorage.setItem('token', 'existing-session')
  localStorage.setItem('user-info', raw)
  useUserStore.getState().hydrateFromStorage()
  expect(useUserStore.getState().token).toBe('existing-session')
  expect(useUserStore.getState().user).toBeNull()
  expect(localStorage.getItem('user-info')).toBe(raw)
})
