/** Load only after the server advertises a configured Google login channel. */
export interface GoogleIdentity {
  initialize(options: {
    client_id: string
    nonce: string
    callback: (response: { credential?: string }) => void
    ux_mode: 'popup'
    auto_select: false
  }): void
  renderButton(element: HTMLElement, options: {
    type: 'standard'; theme: 'outline'; size: 'large'; text: 'continue_with'; width: number; locale: string
  }): void
}

declare global {
  interface Window { google?: { accounts?: { id?: GoogleIdentity } } }
}

let loading: Promise<GoogleIdentity> | undefined
export function loadGoogleIdentity(): Promise<GoogleIdentity> {
  const ready = window.google?.accounts?.id
  if (ready) return Promise.resolve(ready)
  if (loading) return loading
  loading = new Promise<GoogleIdentity>((resolve, reject) => {
    const script = document.createElement('script')
    script.src = 'https://accounts.google.com/gsi/client'
    script.async = true
    const timeout = window.setTimeout(() => fail(), 15000)
    const fail = () => {
      clearTimeout(timeout)
      script.remove()
      loading = undefined
      reject(new Error('Google 登录暂时不可用，请重试或选择其他方式'))
    }
    script.onerror = fail
    script.onload = () => {
      const identity = window.google?.accounts?.id
      if (!identity) { fail(); return }
      clearTimeout(timeout)
      resolve(identity)
    }
    document.head.append(script)
  })
  return loading
}
