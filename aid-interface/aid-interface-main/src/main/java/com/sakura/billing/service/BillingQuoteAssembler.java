package com.sakura.billing.service;

import java.math.BigDecimal;
import java.util.List;

import com.sakura.billing.dto.BillingCalcResult;
import com.sakura.billing.vo.BillingQuoteVO;
import com.sakura.domain.vo.AiModelConfigVo;

/** 将权威计算结果统一组装为用户报价结构。 */
public interface BillingQuoteAssembler
{
    BillingQuoteVO single(String quoteType, AiModelConfigVo modelConfig,
                          BillingCalcResult result, int quantity);

    BillingQuoteVO aggregate(String quoteType, List<BillingQuoteVO> items);

    /** 已确认本轮不会产生模型调用时返回明确零费用，不伪造模型或 SKU。 */
    BillingQuoteVO zero(String quoteType, String displayText);

    /** 将依赖尚未生成业务输入的报价统一标记为预估，不改变权威金额与命中 SKU。 */
    BillingQuoteVO asEstimated(BillingQuoteVO quote);
}
