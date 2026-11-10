package com.sakura.billing.service;

import com.sakura.billing.dto.BillingQuoteRequest;
import com.sakura.billing.vo.BillingQuoteVO;

/** 用户侧只读报价服务。 */
public interface BillingQuoteService
{
    BillingQuoteVO quote(BillingQuoteRequest request, Long userId);
}
