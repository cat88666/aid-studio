import { describe, expect, it } from 'vitest'
import { resolveSubmitAreaBillingView } from './submitAreaBilling'

const base = {
  modelSelected: true,
  hasBillingRules: true,
  hasQuoteRequest: true,
  quoteLoading: false,
  hasQuoteText: false,
  quoteError: ''
}

describe('resolveSubmitAreaBillingView', () => {
  it('shows only the quote hint when a live quote is available', () => {
    expect(
      resolveSubmitAreaBillingView({
        ...base,
        hasQuoteText: true,
        quoteError: '报价暂不可用'
      })
    ).toBe('hint')
  })

  it('shows only the quote hint while the quote is loading', () => {
    expect(
      resolveSubmitAreaBillingView({
        ...base,
        quoteLoading: true
      })
    ).toBe('hint')
  })

  it('shows only the rule preview when quote fails and the model has billing rules', () => {
    expect(
      resolveSubmitAreaBillingView({
        ...base,
        quoteError: '报价暂不可用'
      })
    ).toBe('rule-preview')
  })

  it('shows only the rule preview when there is no quote request yet', () => {
    expect(
      resolveSubmitAreaBillingView({
        ...base,
        hasQuoteRequest: false
      })
    ).toBe('rule-preview')
  })

  it('shows the hint error when quote fails and the model has no billing rules', () => {
    expect(
      resolveSubmitAreaBillingView({
        ...base,
        hasBillingRules: false,
        quoteError: '报价暂不可用'
      })
    ).toBe('hint')
  })

  it('hides billing when no model is selected', () => {
    expect(
      resolveSubmitAreaBillingView({
        ...base,
        modelSelected: false,
        hasQuoteRequest: false
      })
    ).toBe('none')
  })
})
