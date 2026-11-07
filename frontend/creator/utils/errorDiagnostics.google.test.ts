import { describe, expect, it } from 'vitest'
import { sanitizeBrowserDiagnostic } from './errorDiagnostics'

describe('Google authentication diagnostics', () => {
  it('redacts credentials and challenges in nested request/response bodies', () => {
    const result = JSON.stringify(sanitizeBrowserDiagnostic({body: {credential: 'jwt-secret', googleNonce: 'challenge-secret'}, response: {nonce: 'response-secret'}}))
    expect(result).not.toContain('jwt-secret')
    expect(result).not.toContain('challenge-secret')
    expect(result).not.toContain('response-secret')
  })
  it('redacts nonce fields serialized in exception messages', () => {
    const result = JSON.stringify(sanitizeBrowserDiagnostic('request {"googleNonce":"challenge-secret","nonce":"response-secret"}'))
    expect(result).not.toContain('challenge-secret')
    expect(result).not.toContain('response-secret')
  })
})
