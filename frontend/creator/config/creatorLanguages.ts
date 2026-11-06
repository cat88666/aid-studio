export const creatorLanguages = [
  { code: 'zh-hant', label: '繁體中文', path: '/zh-hant/', tag: 'zh-Hant' },
  { code: 'en', label: 'English', path: '/en/', tag: 'en' },
  { code: 'pt', label: 'Português', path: '/pt/', tag: 'pt' },
  { code: 'ko', label: '한국어', path: '/ko/', tag: 'ko' },
  { code: 'es', label: 'Español', path: '/es/', tag: 'es' },
  { code: 'zh', label: '简体中文', path: '/zh/', tag: 'zh-CN' },
  { code: 'ja', label: '日本語', path: '/ja/', tag: 'ja' },
  { code: 'id', label: 'Bahasa Indonesia', path: '/id/', tag: 'id' },
  { code: 'ru', label: 'Русский', path: '/ru/', tag: 'ru' },
  { code: 'fr', label: 'Français', path: '/fr/', tag: 'fr' },
  { code: 'it', label: 'Italiano', path: '/it/', tag: 'it' },
  { code: 'nl', label: 'Nederlands', path: '/nl/', tag: 'nl' },
  { code: 'ar', label: 'العربية', path: '/ar/', tag: 'ar' },
  { code: 'tr', label: 'Türkçe', path: '/tr/', tag: 'tr' },
  { code: 'de', label: 'Deutsch', path: '/de/', tag: 'de' },
  { code: 'uk', label: 'Українська', path: '/uk/', tag: 'uk' },
] as const

export type CreatorLocale = typeof creatorLanguages[number]['code']

export function languageFromPath(path: string) {
  if (!path.replace(/\/+$/, '')) return creatorLanguage('ja')
  return creatorLanguages.find(language => language.path === (path.replace(/\/+$/, '') + '/'))
}

export function creatorLanguage(code: CreatorLocale) {
  return creatorLanguages.find(language => language.code === code)!
}
