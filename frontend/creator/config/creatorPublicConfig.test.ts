import type { AuthPublicConfigData } from '~/types/business-api'
import { describe, expect, it } from 'vitest'
import { creatorPublicConfig } from './creatorPublicConfig'
import { shouldForceMobileOnlyPath } from '~/utils/mobileOnlyNavigation'
import { resolveSiteSeo } from '~/utils/seoHead'

describe('Creator identity isolation', () => {
  it('replaces shared branding and off-site policies without changing authentication or payment capabilities', () => {
    const input = {
      captcha: { enabled: false },
      smsPolicy: { channel: 'sms', codeLength: 6, codeExpireMinutes: 5, sendIntervalSeconds: 60, dailyLimit: 10 },
      emailPolicy: { channel: 'email', codeLength: 6, codeExpireMinutes: 5, sendIntervalSeconds: 60, dailyLimit: 10 },
      basic: { site_name: 'Other site', privacy_policy: 'https://external.example/privacy', terms_of_service: 'https://external.example/terms', exchange_image_url: 'https://external.example/qr.png', open_source_git_url: 'https://external.example/repo', work_publish_enabled: 'true' },
      brand: { platformLogoUrl: 'https://external.example/logo.svg' },
      login: { smsEnabled: false, emailEnabled: false, wechatEnabled: false, googleEnabled: true, googleClientId: 'public-client-id' },
      payment: { alipayEnabled: false, wxpayEnabled: false }
    } satisfies AuthPublicConfigData
    const result = creatorPublicConfig(input)
    expect(JSON.stringify(result)).not.toContain('external.example')
    expect(result.basic.privacy_policy).toBe('/policies/privacy/')
    expect(result.basic.terms_of_service).toBe('/policies/terms/')
    expect(result.basic.work_publish_enabled).toBe('true')
    expect(result.login).toEqual(input.login)
    expect(result.payment).toEqual(input.payment)
    expect(input.basic.site_name).toBe('Other site')
  })

  it('keeps policy and guide chapters accessible on mobile', () => {
    for (const path of ['/policies/terms/', '/policies/privacy/', '/policies/permissions/', '/policies/data/', '/policies/providers/', '/guide/user/', '/guide/developer/']) {
      expect(shouldForceMobileOnlyPath(path)).toBe(false)
    }
    expect(shouldForceMobileOnlyPath('/create/storyboard-video')).toBe(true)
  })

  it('does not import shared SEO branding or canonical destinations', () => {
    const result = resolveSiteSeo('/', { description: 'Other company', keywords: 'Other company', canonicalUrl: 'https://external.example/' }, 'zh')
    expect(result.description).not.toContain('Other company')
    expect(result.keywords).not.toContain('Other company')
    expect(result.canonicalUrl).toBeUndefined()
  })
})
