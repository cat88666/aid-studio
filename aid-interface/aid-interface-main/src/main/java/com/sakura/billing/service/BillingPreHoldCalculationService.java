package com.sakura.billing.service;

import com.sakura.billing.dto.BillingCalcResult;
import com.sakura.billing.dto.BillingInput;
import com.sakura.domain.vo.AiModelConfigVo;

/** 无账户副作用的权威预扣计算链。 */
public interface BillingPreHoldCalculationService
{
    BillingCalcResult calculate(AiModelConfigVo modelConfig, BillingInput billingInput);
}
