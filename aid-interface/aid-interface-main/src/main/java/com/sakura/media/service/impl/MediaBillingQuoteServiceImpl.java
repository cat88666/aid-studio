package com.sakura.media.service.impl;

import org.springframework.stereotype.Service;

import com.sakura.billing.dto.BillingCalcResult;
import com.sakura.billing.service.BillingPreHoldCalculationService;
import com.sakura.billing.service.BillingQuoteAssembler;
import com.sakura.billing.vo.BillingQuoteVO;
import com.sakura.common.exception.ServiceException;
import com.sakura.media.dto.MediaImageGenerateRequest;
import com.sakura.media.dto.PreparedMediaBillingInput;
import com.sakura.media.service.MediaBillingQuotePreparer;
import com.sakura.media.service.MediaBillingQuoteService;

import lombok.RequiredArgsConstructor;

/** 媒体业务请求的统一报价计算实现。 */
@Service
@RequiredArgsConstructor
public class MediaBillingQuoteServiceImpl implements MediaBillingQuoteService
{
    private final MediaBillingQuotePreparer mediaBillingQuotePreparer;
    private final BillingPreHoldCalculationService billingPreHoldCalculationService;
    private final BillingQuoteAssembler billingQuoteAssembler;

    @Override
    public BillingQuoteVO quoteImage(String quoteType, MediaImageGenerateRequest request, int quantity)
    {
        PreparedMediaBillingInput prepared = mediaBillingQuotePreparer.prepareImageBilling(request);
        return assemble(quoteType, prepared, quantity, false);
    }

    @Override
    public BillingQuoteVO quotePlannedImage(
            String quoteType, MediaImageGenerateRequest request, int quantity)
    {
        PreparedMediaBillingInput prepared = mediaBillingQuotePreparer.preparePlannedImageBilling(request);
        return assemble(quoteType, prepared, quantity, true);
    }

    private BillingQuoteVO assemble(String quoteType, PreparedMediaBillingInput prepared,
            int quantity, boolean estimated)
    {
        BillingCalcResult result = billingPreHoldCalculationService.calculate(
                prepared.modelConfig(), prepared.billingInput());
        if (result == null || !result.isMatched())
        {
            throw new ServiceException(result == null ? "计费规则缺失" : result.getErrorMessage());
        }
        BillingQuoteVO quote = billingQuoteAssembler.single(
                quoteType, prepared.modelConfig(), result, quantity);
        return estimated ? billingQuoteAssembler.asEstimated(quote) : quote;
    }
}
