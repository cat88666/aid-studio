package com.sakura.media.dto;

import com.sakura.billing.dto.BillingInput;
import com.sakura.domain.vo.AiModelConfigVo;

/** 通过正式媒体前置校验与归一化后得到的只读计费输入。 */
public record PreparedMediaBillingInput(AiModelConfigVo modelConfig, BillingInput billingInput)
{
}
