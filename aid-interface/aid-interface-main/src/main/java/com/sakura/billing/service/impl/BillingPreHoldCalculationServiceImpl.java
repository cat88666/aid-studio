package com.sakura.billing.service.impl;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.sakura.billing.dto.BillingCalcResult;
import com.sakura.billing.dto.BillingInput;
import com.sakura.billing.enums.BillingConstants;
import com.sakura.billing.estimate.BillingEstimateResolver;
import com.sakura.billing.service.BillingAmountCalculator;
import com.sakura.billing.service.BillingPreHoldCalculationService;
import com.sakura.billing.util.TextReasoningBillingResolver;
import com.sakura.domain.vo.AiModelConfigVo;

import lombok.RequiredArgsConstructor;

/** 统一执行估算、SKU 命中、倍率及账户精度归一化，不写任务或账户。 */
@Service
@RequiredArgsConstructor
public class BillingPreHoldCalculationServiceImpl implements BillingPreHoldCalculationService
{
    private final BillingEstimateResolver billingEstimateResolver;
    private final BillingAmountCalculator billingAmountCalculator;

    @Override
    public BillingCalcResult calculate(AiModelConfigVo modelConfig, BillingInput billingInput)
    {
        TextReasoningBillingResolver.enrich(billingInput, modelConfig);
        billingEstimateResolver.enrichEstimate(billingInput, modelConfig);
        BillingCalcResult result = billingAmountCalculator.calculatePreHoldAmount(modelConfig, billingInput);
        if (result == null || !result.isMatched())
        {
            return result;
        }
        BigDecimal normalized = BillingConstants.normalizeAccountAmount(result.getAmount());
        result.setInputMetadataPending(billingInput.isInputMetadataPending());
        result.setAmount(normalized);
        if (result.getSnapshot() != null)
        {
            result.getSnapshot().setPreHoldAmount(normalized);
        }
        return result;
    }
}
