export type SubmitAreaBillingView = 'none' | 'hint' | 'rule-preview'

export interface SubmitAreaBillingInput {
  modelSelected: boolean
  hasBillingRules: boolean
  hasQuoteRequest: boolean
  quoteLoading: boolean
  hasQuoteText: boolean
  quoteError: string
}

/**
 * 提交区报价只展示一块：成功/加载走权威报价，失败或尚未请求时才回退到模型档位。
 */
export function resolveSubmitAreaBillingView(input: SubmitAreaBillingInput): SubmitAreaBillingView {
  if (!input.modelSelected) return 'none'
  if (input.quoteLoading || input.hasQuoteText) return 'hint'
  if (input.hasBillingRules && (!input.hasQuoteRequest || Boolean(input.quoteError))) {
    return 'rule-preview'
  }
  return 'hint'
}
