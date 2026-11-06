'use client'

import type { BillingQuoteVO, ModelBillingDetailVO } from '~/types/business-api'
import { billingQuoteCreditsForDisplay } from '~/utils/billingQuoteDisplay'
import { resolveSubmitAreaBillingView } from '~/utils/submitAreaBilling'
import { BillingQuoteHint } from './BillingQuoteHint'
import { ModelBillingRules } from './ModelBillingRules'
import './BillingQuoteHint.css'

interface Props {
  modelSelected: boolean
  billing?: ModelBillingDetailVO | null
  hasQuoteRequest: boolean
  quote?: BillingQuoteVO | null
  loading?: boolean
  error?: string
  idleText?: string
}

/** 提交区只展示一块报价：权威报价与模型档位预览互斥。 */
export function SubmitAreaBilling({
  modelSelected,
  billing,
  hasQuoteRequest,
  quote,
  loading = false,
  error = '',
  idleText = ''
}: Props) {
  const view = resolveSubmitAreaBillingView({
    modelSelected,
    hasBillingRules: Boolean(billing?.rules?.length),
    hasQuoteRequest,
    quoteLoading: loading,
    hasQuoteText:
      Boolean(String(quote?.displayText || '').trim()) ||
      Boolean(quote?.isFree) ||
      billingQuoteCreditsForDisplay(quote) != null,
    quoteError: error
  })

  if (view === 'rule-preview' && billing) {
    return (
      <div className="billing-quote-rule-preview">
        <span className="billing-quote-rule-preview__label">当前价格档位</span>
        <ModelBillingRules billing={billing} maxRules={1} showMore={false} />
      </div>
    )
  }

  if (view === 'hint') {
    return (
      <BillingQuoteHint
        quote={quote}
        loading={loading}
        error={error}
        active={modelSelected}
        idleText={idleText}
        className="billing-quote-hint--submit"
      />
    )
  }

  return null
}

export default SubmitAreaBilling
