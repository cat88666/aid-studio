import { create } from 'zustand'

export type AccountSettingsSection =
  | 'password'
  | 'contact'
  | 'wechat'
  | 'sessions'
  | 'history'
  | 'cancel'

interface AccountSettingsStore {
  open: boolean
  section: AccountSettingsSection
  openPanel: (section?: AccountSettingsSection) => void
  closePanel: () => void
  setSection: (section: AccountSettingsSection) => void
}

export const useAccountSettingsStore = create<AccountSettingsStore>((set) => ({
  open: false,
  section: 'password',
  openPanel: (section = 'password') => set({ open: true, section }),
  closePanel: () => set({ open: false }),
  setSection: (section) => set({ section })
}))

export function openAccountSettings(section: AccountSettingsSection = 'password') {
  useAccountSettingsStore.getState().openPanel(section)
}

export function closeAccountSettings() {
  useAccountSettingsStore.getState().closePanel()
}
